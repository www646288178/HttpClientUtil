package com.wondersgroup.http;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by lenovo on 2018/3/19.
 * HttpClientBuilder：非线程安全，但同一个实例创建的connection均在PoolingHttpClientConnectionManager管理下，可自动实现HttpClient的回收
 */
public class HttpClientUtil {
    private final static Logger logger = Logger.getLogger(HttpClientUtil.class);
    private static HttpClientUtil THIS_INSTANCE;
    private HttpClientBuilder httpClientBuilder;
    private HttpClientBuilder httpClientBuilderSSL;
    private final static int TIME_OUT = 30000; //30S
    private final static int MAX_CONN_TOTAL = 50;//连接池的最大链接数
    private final static int MAX_CONN_PER_ROUTE = 10;//每个url可以建立的最大连接数
    private final static int POOL_SOCKET_TIME_OUT = 5*60*1000;//连接池
    private static final String CHARSET = "UTF-8";

    /**
     * 单例模式实现保证所有的连接都出自同一个HttpClientBuilder对象
     * @return
     */
    public final synchronized static HttpClientUtil getInstance() {
        if(THIS_INSTANCE==null) {
            THIS_INSTANCE = new HttpClientUtil();
        }
        return THIS_INSTANCE;
    }

    /**
     * 初始化httpClientBuilder
     */
    private HttpClientUtil() {
        httpClientBuilder = HttpClientBuilder.create();
        httpClientBuilderSSL = HttpClientBuilder.create();
        RequestConfig requestConfig = RequestConfig.custom()  //此参数影响的是closeablehttpresponse
                .setSocketTimeout(TIME_OUT)  // 数据传输的最长时间
                .setConnectTimeout(TIME_OUT) // 创建连接的最长时间
                .setConnectionRequestTimeout(TIME_OUT) // 从连接池中获取到连接的最长时间
                .build();

        SocketConfig socketConfig = SocketConfig.custom()  //影响PoolingHttpClientConnectionManager的配置
                .setSoTimeout(POOL_SOCKET_TIME_OUT).build();

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setCharset(Charset.forName(CHARSET)).build();

        httpClientBuilder.setMaxConnTotal(MAX_CONN_TOTAL).setMaxConnPerRoute(MAX_CONN_PER_ROUTE)
                .setDefaultRequestConfig(requestConfig).setDefaultSocketConfig(socketConfig).setDefaultConnectionConfig(connectionConfig);


        //https SSL配置
        //https配置
        SSLConnectionSocketFactory sslConnectionSocketFactory = null;
        try {
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    return false;
                }
            }).build();
            sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
                    new String[]{"SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.2"}, null, NoopHostnameVerifier.INSTANCE);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        httpClientBuilderSSL.setSSLSocketFactory(sslConnectionSocketFactory).setMaxConnTotal(MAX_CONN_TOTAL).setMaxConnPerRoute(MAX_CONN_PER_ROUTE)
                .setDefaultRequestConfig(requestConfig).setDefaultSocketConfig(socketConfig).setDefaultConnectionConfig(connectionConfig);

    }

    /**
     * 创建连接
     * @return
     */
    private final CloseableHttpClient getHttpClient() {
        synchronized (httpClientBuilder) {
            return  httpClientBuilder.build();
        }
    }

    /**
     * 创建连接 https
     * @return
     */
    private final CloseableHttpClient getHttpClientSSL() {
        synchronized (httpClientBuilderSSL) {
            return  httpClientBuilderSSL.build();
        }
    }


    /**
     * GET请求
     * @param url
     * @return
     */
    public final String doGet(String url) {
        CloseableHttpClient closeableHttpClient = this.getHttpClient();
        CloseableHttpResponse httpResponse = null;
        HttpGet httpGet = new HttpGet(url);

        String content = null;
        try {
            httpResponse = closeableHttpClient.execute(httpGet);
            HttpEntity httpEntity = httpResponse.getEntity();
            content = EntityUtils.toString(httpEntity,"UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(httpResponse!=null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return content;
    }

    /**
     * GET请求
     * @param url
     * @param username
     * @param password
     * @return
     */
    public final String doGet(String url,String username,String password) {
        CloseableHttpClient closeableHttpClient = this.getHttpClient();

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(username, password));

        // Add AuthCache to the execution context
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        CloseableHttpResponse httpResponse = null;
        HttpGet httpGet = new HttpGet(url);

        String content = null;
        try {
            httpResponse = closeableHttpClient.execute(httpGet,context);
            HttpEntity httpEntity = httpResponse.getEntity();
            content = EntityUtils.toString(httpEntity,"UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(httpResponse!=null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return content;
    }

    /**
     * GET请求 https
     * @param url
     * @return
     */
    public final String doGetSSL(String url) {
        CloseableHttpClient closeableHttpClient = this.getHttpClientSSL();
        CloseableHttpResponse httpResponse = null;
        HttpGet httpGet = new HttpGet(url);

        String content = null;
        try {
            httpResponse = closeableHttpClient.execute(httpGet);
            HttpEntity httpEntity = httpResponse.getEntity();
            content = EntityUtils.toString(httpEntity,"UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(httpResponse!=null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return content;
    }

    /**
     * GET请求 https
     * @param url
     * @param username
     * @param password
     * @return
     */
    public final String doGetSSL(String url,String username,String password) {
        CloseableHttpClient closeableHttpClient = this.getHttpClientSSL();

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(username, password));

        // Add AuthCache to the execution context
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        CloseableHttpResponse httpResponse = null;
        HttpGet httpGet = new HttpGet(url);

        String content = null;
        try {
            httpResponse = closeableHttpClient.execute(httpGet,context);
            HttpEntity httpEntity = httpResponse.getEntity();
            content = EntityUtils.toString(httpEntity,"UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(httpResponse!=null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return content;
    }

    /**
     * POST请求
     * @param url
     * @param param  json格式的参数：{aac001:111,aac147:xxx}
     * @return
     */
    public final String doPost(String url,String param) {
        CloseableHttpClient closeableHttpClient = this.getHttpClient();
        CloseableHttpResponse httpResponse = null;
        HttpPost httpPost = new HttpPost(url);
        HttpEntity httpEntity = null;
        String content = null;
        try {
            httpPost.setEntity(new StringEntity(param, ContentType.create("application/json", "UTF-8")));
            httpResponse = closeableHttpClient.execute(httpPost);
            httpEntity = httpResponse.getEntity();
            content = EntityUtils.toString(httpEntity,"UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(httpResponse!=null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return content;
    }

    /**
     * POST请求
     * @param url
     * @param param json格式的参数：{aac001:111,aac147:xxx}
     * @param username
     * @param password
     * @return
     */
    public final String doPost(String url,String param,String username,String password) {
        CloseableHttpClient closeableHttpClient = this.getHttpClient();

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(username, password));

        // Add AuthCache to the execution context
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);


        HttpPost httpPost = new HttpPost(url);
        HttpEntity httpEntity = null;
        String content = null;
        //jdk1.7新特性，实现Closeable接口的可以自动关闭
        try(CloseableHttpResponse httpResponse = closeableHttpClient.execute(httpPost,context)) {
            httpPost.setEntity(new StringEntity(param, ContentType.create("application/json", "UTF-8")));
            httpEntity = httpResponse.getEntity();
            content = EntityUtils.toString(httpEntity,"UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }


    /**
     * 用户名密码校验连接
     * @param args
     */
   /* public static void main(String[] args) {
        System.out.println(HttpClientUtil.getInstance().doGet("http://master:7180/api/v13/timeseries","admin","admin"));
    }*/
    /**
     * test:500连接没有问题
     * @param
     */
   /* public static void main(String[] args) {
        System.out.println(HttpClientUtil.getInstance().doGet("https://www.baidu.com/"));
       *//* ExecutorService executorService = Executors.newFixedThreadPool(500);
        for(int i=0;i<500;i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getId()+" start ------"+System.currentTimeMillis());
                    System.out.println(HttpClientUtil.getInstance().doGet("http://15.72.10.155:50070/jmx?qry=java.lang:type=Memory"));
                    System.out.println(Thread.currentThread().getId()+" end ------"+System.currentTimeMillis());
                }
            });
        }

        executorService.shutdown();*//*
    }*/
   /* public static void main(String[] args) {
        JFrame jf=new JFrame("参数配置");//实例化一个JFrame对象
        Container container=jf.getContentPane();//获取一个容器
//        container.setBackground(Color.cyan);//设置容器的背景颜色
        container.setLayout(new BorderLayout());
        JPanel jPanel = new JPanel();
        JLabel jLabel = new JLabel("URL");
        final JTextField jTextField2 = new JTextField(50);
        JButton jButton = new JButton("测试");
        jButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String url = jTextField2.getText();
                if(url!=null&&!url.equals("")) {
                    String val = HttpClientUtil.getInstance().doGet(url);
                    if(val!=null&&!val.equals(""))
                        JOptionPane.showMessageDialog(null,"连接成功");
                    else
                        JOptionPane.showMessageDialog(null,"连接失败");
                }else {
                    JOptionPane.showMessageDialog(null,"请输入url");
                }

            }
        });
        jPanel.add(jLabel);
        jPanel.add(jTextField2);
        jPanel.add(jButton);
        container.add(jPanel);
        jf.setVisible(true);//使窗体可视
        jf.setBounds(200,200,800,650);//设置窗体的位置和大小
        //设置窗体的关闭方式
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    }
*/

}
