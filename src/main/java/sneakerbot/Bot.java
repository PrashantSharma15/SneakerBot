package main.java.sneakerbot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Random;
import main.java.sneakerbot.atc.Adidas;
import main.java.sneakerbot.atc.Shopify;
import main.java.sneakerbot.atc.Supreme;
import main.java.sneakerbot.captcha.Harvester;
import main.java.sneakerbot.captcha.Harvester.CaptchaResponse;
import main.java.sneakerbot.loaders.AdidasAccount;
import main.java.sneakerbot.loaders.AdidasAccount.AccountObject;
import main.java.sneakerbot.loaders.Config;
import main.java.sneakerbot.loaders.Config.ConfigObject;
import main.java.sneakerbot.loaders.Credentials;
import main.java.sneakerbot.loaders.Credentials.CredentialObject;
import main.java.sneakerbot.loaders.Proxy;
import main.java.sneakerbot.loaders.Proxy.ProxyObject;
import main.java.sneakerbot.thread.ThreadPool;
import main.java.sneakerbot.utils.AdidasAccountCreator;
import main.java.sneakerbot.utils.PingTest;
import main.java.sneakerbot.utils.SitekeyGrabber;

public class Bot {
	
	public enum Task {
	    ADIDAS,
	    MESH,
	    SHOPIFY,
	    SUPREME,
	    ACCOUNTCREATOR,
	    SITEKEYGRABBER,
		PINGTEST;
	}
	
	static ThreadPool pool;
	static ArrayList<AccountObject> accounts;
	static int usedAccounts;
	static ArrayList<ProxyObject> proxies;
	static ArrayList<ProxyObject> usedProxies;
	static Map<String, CredentialObject> credentials;
	static ArrayList<ConfigObject> configs;
	static int taskCount;
	public static List<CaptchaResponse> captchas;
	
	public static void init() {
		taskCount = 0;
		usedAccounts = 0;
		accounts = AdidasAccount.load("data/accounts.txt");
		proxies = Proxy.load("data/proxies.txt");
		usedProxies = new ArrayList<ProxyObject>();
		credentials = Credentials.load("data/credentials.json");
		configs = Config.load("data/config.json");
		captchas = new ArrayList<CaptchaResponse>();
		
		for (Object config : configs.stream().toArray())
			taskCount += (int)((ConfigObject) config).getTasks();
		
		pool = new ThreadPool(taskCount);
	}

	public static void main(String[] args) {
		init();
		
		if(configs == null)
			return;
		
		configs.stream().forEach(c -> {
			
			if(c.getTasks() == 0)
				return;
			
			CredentialObject creds = null;
			
			try {
				creds = credentials.get(c.getPayment());
			} catch (Exception e) { System.out.println(e.getMessage());}
			
			if(!Harvester.running && c.getSiteKey() != null && !c.getSiteKey().isEmpty())
				new Harvester(c.getSiteKey());
			
			if(c.getTaskType() == Task.ACCOUNTCREATOR)
				new AdidasAccountCreator(getRandomProxy(), c.getTasks()).start();
			
			if(c.getTaskType() == Task.SITEKEYGRABBER)
				new SitekeyGrabber(getRandomProxy()).check();
			
			for (int start = 0; start < c.getTasks(); start++)  {
				if(c.getTaskType() == Task.ADIDAS) {
					pool.run(new Adidas(getRandomProxy(), creds, c, getRandomAccount(), start));
				} else if(c.getTaskType() == Task.SHOPIFY) {
					pool.run(new Shopify("kith", getRandomProxy(), creds, c));
				} else if(c.getTaskType() == Task.SUPREME) {
					pool.run(new Supreme(getRandomProxy(), creds, c));				
				} else if(c.getTaskType() == Task.PINGTEST) {
					pool.run(new PingTest(getRandomProxy(),  c));			
				}
			}
		});
		

		//print("Proxies loaded: " + (proxies.size() + inUse.size()) + "\nTasks loaded: " + taskCount + "\nPress Enter to start tasks.");
		print("Accounts loaded: " + (accounts.size() + usedAccounts));
		print("Proxies loaded: " + (proxies.size() + usedProxies.size()));
		print("Tasks loaded: " + taskCount);
		print("Press Enter to start tasks.");
		try{System.in.read();}
		catch(Exception e){}
		
		pool.flush();
		
	
	}
	
	public static AccountObject getRandomAccount() {
		int count = accounts.size();
		
		if(count == 0)
			return null; 
		
		usedAccounts++;
		return accounts.remove(new Random().nextInt(count));
	}
	
	public static ProxyObject getRandomProxy() {
		int proxyCount = proxies.size();
		int usedCount = usedProxies.size();
		
		if(proxyCount == 0 && usedCount == 0)
			return null; 
		
		int index = new Random().nextInt(proxyCount != 0 ? proxyCount : usedCount);
		if(proxyCount != 0) {
			ProxyObject proxy = proxies.remove(index);
			usedProxies.add(proxy);
			return proxy;
		} else 
			return usedProxies.get(index);

	}
	
	public static void print(Object text) {
		System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()) + "][Bot] " + text.toString());
	}
}
