package main.java.sneakerbot.loaders;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import main.java.sneakerbot.Bot;

public class Config {
	
	public static ArrayList<ConfigObject> load(String name) {
        File file = new File(name); 
        
        if(!file.exists()) {
        	System.out.println(name + " does not exist; One has been created for you.");
        	create(name);
        	return null;
        }
        
        Type type = new TypeToken<ArrayList<ConfigObject>>() { }.getType();
		try {
			return new GsonBuilder().create().fromJson(new FileReader(name), type);
		} catch (JsonIOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonSyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static void create(String name) {
        ArrayList<ConfigObject> configs = new ArrayList<ConfigObject>();
        
        configs.add(new ConfigObject(Bot.Task.ADIDAS, "BA8842", false, true, true, new double[] {8, 8.5, 11, 12.5}, "CC 1", 0));
        configs.add(new ConfigObject(Bot.Task.SUPREME, "Leather Bones Jacket", "Thu, 23 Nov 2017 16:00:00 GMT", "CC 2", 0));
        configs.add(new ConfigObject(Bot.Task.ACCOUNTCREATOR, 0));
        
		try (FileWriter writer = new FileWriter(name)) {
			new GsonBuilder().enableComplexMapKeySerialization()
				.setPrettyPrinting().create().toJson(configs, writer);
		} catch (IOException e) { e.printStackTrace(); }
		
	}
	
	public static class ConfigObject {
		
		public ConfigObject(Bot.Task taskType, String sku, boolean grabCaptcha, boolean splash, boolean manual, double[] sizes, String payment, int tasks) {
			super();
			this.taskType = taskType;
			this.sku = sku;
			this.grabCaptcha = grabCaptcha;
			this.splash = splash;
			this.manual = manual;
			this.sizes = sizes;
			this.payment = payment;
			this.tasks = tasks;
		}
		
		public ConfigObject(Bot.Task taskType, String keyword, String releaseTime, String payment, int tasks) {
			super();
			this.taskType = taskType;
			this.keyword = keyword;
			this.releaseTime = releaseTime;
			this.payment = payment;
			this.tasks = tasks;	
		}
		
		public ConfigObject(Bot.Task taskType, int tasks) {
			this.taskType = taskType;	
			this.tasks = tasks;	
		}
		
		public Bot.Task getTaskType() {
			return taskType;
		}
		
		
		public String getKeyword() {
			return keyword;
		}

		public String getReleaseTime() {
			return releaseTime;
		}
		
		public String getSku() {
			return sku;
		}
		
		public boolean grabCaptcha() {
			return grabCaptcha;
		}
		
		public boolean isSplash() {
			return splash;
		}
		
		public boolean isManual() {
			return manual;
		}
		
		public double[] getSizes() {
			return sizes;
		}
		
		public String getPayment() {
			return payment;
		}
		
		public int getTasks() {
			return tasks;
		}

		@Override
		public String toString() {
			return "ConfigObject [taskType=" + taskType + ", keyword=" + keyword + ", releaseTime=" + releaseTime
					+ ", sku=" + sku + ", grabCaptcha=" + grabCaptcha + ", splash=" + splash + ", manual=" + manual
					+ ", sizes=" + Arrays.toString(sizes) + ", payment=" + payment + ", tasks=" + tasks + "]";
		}

		private Bot.Task taskType;
		//SUPREME
		private String keyword;
		private String releaseTime;
		//ADIDAS
		private String sku;
		private boolean grabCaptcha;
		private boolean splash;
		private boolean manual;
		private double[] sizes;
		
		
		private String payment;
		private int tasks;
	}
}
