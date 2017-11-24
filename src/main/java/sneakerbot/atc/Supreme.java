package main.java.sneakerbot.atc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import main.java.sneakerbot.Bot;
import main.java.sneakerbot.loaders.Config.ConfigObject;
import main.java.sneakerbot.loaders.Credentials.CredentialObject;
import main.java.sneakerbot.loaders.Proxy.ProxyObject;

public class Supreme implements Runnable  {
	
	public Supreme(ProxyObject proxy, CredentialObject credentials, ConfigObject config) {
		super();	
		
		BasicCredentialsProvider proxyCredentials = null;
		cookies = new BasicCookieStore();
		final int timeout = 15000;
		
		if(proxy != null && proxy.getUsername() != null) {
			proxyCredentials = new BasicCredentialsProvider();
			proxyCredentials.setCredentials(
					new AuthScope(proxy.getAddress(), proxy.getPort()),
					new UsernamePasswordCredentials(proxy.getUsername(), proxy.getPassword()));			
		}
		
		client = HttpClientBuilder.create()
				.setDefaultCookieStore(cookies)
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
		
		this.keyword = config.getKeyword();
		this.release_time = config.getReleaseTime();
		this.proxy = proxy;
		this.credentials = credentials;
		sleep = 0L;
		DEBUG = true;
	}
	
	@Override
	public void run() {
		if(keyword == null || release_time == null) {
			print("keyword or release time is null.");
			return;		
		}
			
		int productId = getProductId(keyword);
		
		if(productId == -1) {
			print("Product Ids is -1");
			return;
		}
		
		List<String> variants = getVariants(productId);
		List<Runnable> tasks = new ArrayList<Runnable>();	
		List<Thread> threads = new ArrayList<Thread>();
		
		if(variants.size() == 0) {
			print("Items are out of stock.");
			return;		
		}
		
		for(String variant : variants) {
			tasks.add(() -> {
				boolean carted = false;
				
				while(!carted && !Thread.interrupted())
					carted = addToCart(productId, variant);
				
				if(carted) {
					boolean checkout = false;
					
					while(!checkout && !Thread.interrupted())
						checkout = checkout(variant);		

					if(checkout)
						print("Successful checkout on item: " + productId + " Variant: " + variant);
					else
						print("Unsuccessful checkout on item: " + productId + " Variant: " + variant);					
				}					
			});
		}
		
		tasks.add(() -> {		
		    synchronized(this) {
		    	if(Bot.captchas.size() >= 1) {
		    		token = Bot.captchas.remove(new Random().nextInt(Bot.captchas.size())).getToken();
		    		tokenGrabTime = System.currentTimeMillis();
		    	}   	
		    }

		    while(!itemCarted && !Thread.interrupted()) {
				if((System.currentTimeMillis() - tokenGrabTime) > 90000L && tokenGrabTime != 0L) { // grab new recaptcha token
				    synchronized(this) {
				    	if(Bot.captchas.size() >= 1) {
				    		token = Bot.captchas.remove(new Random().nextInt(Bot.captchas.size())).getToken();
				    		tokenGrabTime = System.currentTimeMillis();
				    	}   
				    }
				}	    	
		    }	    
		});
		
		tasks.stream().forEach(t -> {
			threads.add(new Thread(t));
		});
		
		if(sleep > 0L) {
			int totalSecs = (int) sleep;
			int hours = totalSecs / 3600;
			int minutes = (totalSecs % 3600) / 60;
			int seconds = totalSecs % 60;
			String timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
			
			try {
				print("Sleeping for " + timeString);
				Thread.sleep((sleep * 1000L) - 5000L); // sleep til 5 seconds before release time :)
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}

		threads.stream().forEach(t -> { // start atc & checkout process
			t.start();
			
		});
		
	    synchronized(this) {
			while(!itemCarted) { // check if any threads checked out.
				try {
					Thread.sleep(2500L);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
	    }
		
		threads.stream().forEach(t -> { // stop threads if item checked out.
			t.interrupt();
		});	
	}
	
	public int getProductId(String keyword) {
		HttpGet request = new HttpGet("http://www.supremenewyork.com/mobile_stock.json");
		HttpResponse response = null;
		
		request.setHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_3 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Mobile/13G34");
		request.setHeader("Accept", "application/json");
		request.setHeader("Accept-Encoding", "gzip, deflate");
		request.setHeader("Accept-Language", "en-US,en;q=0.8");
		request.setHeader("Connection", "keep-alive");
		request.setHeader("Host", "www.supremenewyork.com");;
		request.setHeader("Referer", "http://www.supremenewyork.com/mobile");
		request.setHeader("X-Requested-With", "XMLHttpRequest");
		
		while(response == null) { // Just incase client cant execute the request :)
			try {
				response = client.execute(request);
			       
				BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				StringBuffer result = new StringBuffer();
				String line = "";
				while ((line = in.readLine()) != null) 
					result.append(line);
				
				in.close();
				
				JSONObject reponse = new JSONObject(result.toString()).getJSONObject("products_and_categories");
				
				for(String key : reponse.keySet()) {			
					for(Object itemJson : reponse.getJSONArray(key)) {
						JSONObject item = new JSONObject(itemJson.toString());
						String name = item.getString("name");
						
						if(name.toLowerCase().contains(keyword.toLowerCase())) {
							int productId = item.getInt("id");
							print("Found: " + name + "! Product ID: " + productId);
							return productId;
						}			
					}
				}
				print("Product not found using keyword: " + keyword);
				return -1;
			} catch (Exception e ) {
				if(DEBUG) 
					e.printStackTrace();
				else {
					String name = e.getClass().getName();
					
					if(!name.contains("SocketTimeoutException"))
						print("[Exception - cart(size)] -> " + name);
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
		return -1;
	}
	
	public List<String> getVariants(int productId) {
		HttpGet request = new HttpGet("http://www.supremenewyork.com/shop/" + productId + ".json");
		HttpResponse response = null;
		List<String> variants = new ArrayList<String>();
		
		request.setHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_3 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Mobile/13G34");
		request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		request.setHeader("Accept-Encoding", "gzip, deflate, sdch");
		request.setHeader("Accept-Language", "en-US,en;q=0.8");
		request.setHeader("Cache-Control", "max-age=0");
		request.setHeader("Connection", "keep-alive");
		request.setHeader("Host", "www.supremenewyork.com");
		request.setHeader("Upgrade-Insecure-Requests", "1");
		
		while(response == null) { // Just incase client cant execute the request :)
			try {
				response = client.execute(request);
			       
				BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				StringBuffer result = new StringBuffer();
				String line = "";
				while ((line = in.readLine()) != null) 
					result.append(line);
				
				in.close();
				
				for(Object itemJson : new JSONObject(result.toString()).getJSONArray("styles")) {
					JSONObject item = new JSONObject(itemJson.toString());
					
					for(Object sizeJson : item.getJSONArray("sizes")) {
						JSONObject size = new JSONObject(sizeJson.toString());
						
						if(size.getInt("stock_level") > 0) {
							if(DEBUG)
								print("Adding size: " + size.getString("name") + " for color: " + item.getString("name") + ". sizeId: " + size.getInt("id"));
							
							variants.add(Integer.toString(size.getInt("id")));
						}
					}
				}
				
				long currTime = ZonedDateTime.parse(response.getFirstHeader("Date").getValue(), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond();
				long releaseTime = ZonedDateTime.parse(release_time, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond();
				
				sleep = releaseTime - currTime; // Is this a thing?
				
			} catch (Exception e ) {
				if(DEBUG) 
					e.printStackTrace();
				else {
					String name = e.getClass().getName();
					
					if(!name.contains("SocketTimeoutException"))
						print("[Exception - cart(size)] -> " + name);
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
		return variants;
	}
	
	public boolean addToCart(int productId, String variant) {
		HttpPost request = new HttpPost("http://www.supremenewyork.com/shop/" + productId + "/add.json");
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		HttpResponse response = null;
		
		request.setHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_3 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Mobile/13G34");
		request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		request.setHeader("Accept-Encoding", "gzip, deflate, sdch");
		request.setHeader("Accept-Language", "en-US,en;q=0.8");
		request.setHeader("Connection", "keep-alive");
		request.setHeader("Host", "www.supremenewyork.com");
		request.setHeader("Upgrade-Insecure-Requests", "1");
		
		data.add(new BasicNameValuePair("size", Integer.toString(productId)));
		data.add(new BasicNameValuePair("style", variant));
		data.add(new BasicNameValuePair("qty", "1"));
		
		try {
			request.setEntity(new UrlEncodedFormEntity(data));
			response = client.execute(request);
			       
			BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			StringBuffer result = new StringBuffer();
			String line = "";
			while ((line = in.readLine()) != null) 
				result.append(line);
				
			in.close();
				
			print(result.toString());
			if(response.getStatusLine().getStatusCode() == 200 && !result.toString().equalsIgnoreCase("[]")) {
				print("Product: " + productId + " Variant: " + variant + " added to cart.");
				return true;
			} else 
				print("Status Code: " + response.getStatusLine().getStatusCode() + " Body: " + result.toString());
				
			Thread.sleep(new Random().nextInt((int) (3500L - 1500L) + 1) + 1500L); // sleep random time 1.5-3 secs
		} catch (Exception e ) {
			if(DEBUG) 
				e.printStackTrace();
			else {
				String name = e.getClass().getName();
					
				if(!name.contains("SocketTimeoutException"))
					print("[Exception - addToCart(productId, variant)] -> " + name);
			}
		} finally {
			if(request != null)
				request.releaseConnection();
			try {
				if(response != null && response.getEntity() != null)
					EntityUtils.consume(response.getEntity());
			} catch (Exception e) { e.printStackTrace(); }
		}
		return false;
	}
	
	public boolean checkout(String variant) {
		HttpPost request = new HttpPost("https://www.supremenewyork.com/checkout.json");
		HttpResponse response = null;
		
		request.setHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_3 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Mobile/13G34");
		request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		request.setHeader("Accept-Encoding", "gzip, deflate, sdch");
		request.setHeader("Accept-Language", "en-US,en;q=0.8");
		request.setHeader("Connection", "keep-alive");
		request.setHeader("Host", "www.supremenewyork.com");
		request.setHeader("Upgrade-Insecure-Requests", "1");
		
		List<NameValuePair> data = generateCheckout(variant);
		
		try {
				
			request.setEntity(new UrlEncodedFormEntity(data));
			response = client.execute(request);
			       
			BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			StringBuffer result = new StringBuffer();
			String line = "";
			while ((line = in.readLine()) != null) 
				result.append(line);
				
			in.close();
				
			print(result.toString());
			if(response.getStatusLine().getStatusCode() == 200) {
				JSONObject checkoutJson = new JSONObject(result.toString());
				if(!checkoutJson.getString("status").toLowerCase().equals("failed")) {
					itemCarted = true;
					return true;
				} else
					print("Checkout status: " + checkoutJson.getString("status"));
			} else 
				print("Status Code: " + response.getStatusLine().getStatusCode() + " Body: " + result.toString());
				
			Thread.sleep(new Random().nextInt((int) (3500L - 1500L) + 1) + 1500L); // sleep random time 1.5-3 secs
		} catch (Exception e ) {
			if(DEBUG) 
				e.printStackTrace();
			else {
				String name = e.getClass().getName();
					
				if(!name.contains("SocketTimeoutException"))
					print("[Exception - addToCart(productId, variant)] -> " + name);
			}
		} finally {
			if(request != null)
				request.releaseConnection();
			try {
				if(response != null && response.getEntity() != null)
					EntityUtils.consume(response.getEntity());
			} catch (Exception e) { e.printStackTrace(); }
		}
		return false;
	}
	
	public List<NameValuePair> generateCheckout(String variant) {
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		
		data.add(new BasicNameValuePair("store_credit_id", "1"));
		data.add(new BasicNameValuePair("from_mobile", "1"));
		try {
			data.add(new BasicNameValuePair("cookie-sub", URLEncoder.encode("{" + variant + ":1}", "UTF-8")));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		data.add(new BasicNameValuePair("same_as_billing_address", "1"));
		data.add(new BasicNameValuePair("order[billing_name]", credentials.getBilling().getFirstName() + " " + credentials.getBilling().getLastName()));
		data.add(new BasicNameValuePair("order[email]", credentials.getEmail()));
		data.add(new BasicNameValuePair("order[tel]", credentials.getBilling().getPhone()));
		data.add(new BasicNameValuePair("order[billing_address]", credentials.getBilling().getAddress()));
		data.add(new BasicNameValuePair("order[billing_address_2]", ""));
		data.add(new BasicNameValuePair("order[billing_zip]", credentials.getBilling().getZip()));
		data.add(new BasicNameValuePair("order[billing_city]", credentials.getBilling().getCity()));
		data.add(new BasicNameValuePair("order[billing_state]", credentials.getBilling().getState()));
		data.add(new BasicNameValuePair("order[billing_country]", "USA"));
		data.add(new BasicNameValuePair("store_address", "1"));
		data.add(new BasicNameValuePair("credit_card[type]", CardType.detect(credentials.getCard().getNumber()).toString().toLowerCase()));
		data.add(new BasicNameValuePair("credit_card[cnb]", credentials.getCard().getNumber()));
		data.add(new BasicNameValuePair("credit_card[month]", credentials.getCard().getMonth()));
		data.add(new BasicNameValuePair("credit_card[year]", credentials.getCard().getYear()));
		data.add(new BasicNameValuePair("credit_card[vval]", credentials.getCard().getCode()));
		data.add(new BasicNameValuePair("order[terms]", "0"));
		data.add(new BasicNameValuePair("order[terms]", "1"));
		data.add(new BasicNameValuePair("is_from_ios_native", "1"));		
		data.add(new BasicNameValuePair("g-recaptcha-response", token));	    	
		
		return data;
	}

	public void print(Object text) {
		System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + "][Supreme] " + text.toString());
	}
	
	private String release_time;
	private String keyword;
	String token;
	long tokenGrabTime = 0L;
	boolean itemCarted;
	long sleep;
	CookieStore cookies;
	HttpClient client;
	ProxyObject proxy;
	CredentialObject credentials;
	boolean DEBUG;
	
	public enum CardType {

	    UNKNOWN,
	    VISA("^4[0-9]{12}(?:[0-9]{3})?$"),
	    MASTERCARD("^5[1-5][0-9]{14}$"),
	    AMERICAN_EXPRESS("^3[47][0-9]{13}$"),
	    DINERS_CLUB("^3(?:0[0-5]|[68][0-9])[0-9]{11}$"),
	    DISCOVER("^6(?:011|5[0-9]{2})[0-9]{12}$"),
	    JCB("^(?:2131|1800|35\\d{3})\\d{11}$");

	    private Pattern pattern;

	    CardType() {
	        this.pattern = null;
	    }

	    CardType(String pattern) {
	        this.pattern = Pattern.compile(pattern);
	    }

	    public static CardType detect(String cardNumber) {

	        for (CardType cardType : CardType.values()) {
	            if (null == cardType.pattern) continue;
	            if (cardType.pattern.matcher(cardNumber).matches()) return cardType;
	        }

	        return UNKNOWN;
	    }
	}
}
