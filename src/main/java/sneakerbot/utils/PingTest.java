package main.java.sneakerbot.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.util.EntityUtils;

import main.java.sneakerbot.loaders.Config.ConfigObject;
import main.java.sneakerbot.loaders.Proxy.ProxyObject;

public class PingTest implements Runnable {
	
	HttpClient client;
	ProxyObject proxy;
	boolean debug;
	String host;
	
	public PingTest(ConfigObject config, ProxyObject proxy) {
		super();	
		
		BasicCredentialsProvider proxyCredentials = null;
		final int timeout = 15000;
		
		if(proxy != null && proxy.getUsername() != null) {
			proxyCredentials = new BasicCredentialsProvider();
			proxyCredentials.setCredentials(
					new AuthScope(proxy.getAddress(), proxy.getPort()),
					new UsernamePasswordCredentials(proxy.getUsername(), proxy.getPassword()));			
		}
		
		client = HttpClientBuilder.create()
				.setDefaultCookieStore(new BasicCookieStore())
				.setRoutePlanner(proxy != null ? new DefaultProxyRoutePlanner(new HttpHost(proxy.getAddress(), proxy.getPort())) : null)
				.setConnectionReuseStrategy( (response, context) -> false )
				.setDefaultCredentialsProvider(proxyCredentials)
				.setDefaultRequestConfig(RequestConfig.custom()
						.setCookieSpec(CookieSpecs.STANDARD)
						.setProxy(proxy != null ? new HttpHost(proxy.getAddress(), proxy.getPort()) : null)
						.setConnectTimeout(timeout)
						.setConnectionRequestTimeout(timeout)
						.setSocketTimeout(timeout)
						.build())
				.build();	
		
		this.proxy = proxy;
		host = "http://www.adidas.com/"; // add to config
		debug = true;
	}
	

	@Override
	public void run() {
		if(proxy == null) {
			print("Proxy is null, please add valid proxies and restart.");
			return;			
		}
		
		if(!host.isEmpty()) {
			long ping = test(host);
			print(proxy.getAddress() + ":" + proxy.getPort() + " -> " + ping + "ms.");
		} else
			print("host is not set.");	
	}
	
	public long test(String url) {
		HttpGet request = new HttpGet(url);
		HttpResponse response = null;
		
		request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.98 Safari/537.36");
		request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		request.setHeader("Accept-Encoding", "gzip, deflate, sdch");
		request.setHeader("Accept-Language", "en-US,en;q=0.8");		
		request.setHeader("Connection", "keep-alive");
		request.setHeader("Upgrade-Insecure-Requests", "1");
		
		while(response == null) {
			try {
				long start = System.currentTimeMillis();
				response = client.execute(request);
				if(response.getStatusLine().getStatusCode() == 200)
					return System.currentTimeMillis() - start;
				else 
					print("Error - Code: " + response.getStatusLine().getStatusCode());
			} catch (Exception e) {
				if(debug) 
					e.printStackTrace();
				else {
					String name = e.getClass().getName();
					
					if(!name.contains("SocketTimeoutException"))
						print("[Exception - request()] -> " + name);
				}
			} finally {
				if(request != null)
					request.releaseConnection();
				try {
					if(response != null && response.getEntity() != null)
						EntityUtils.consume(response.getEntity());
				} catch (Exception e) { e.printStackTrace(); }
			}
		}
		return 696969;
	}

	public void print(String text) {
		System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + "][PingTest] " + text);
	}
}
