package main.java.sneakerbot.loaders;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class AdidasAccount {
	
	public static ArrayList<AccountObject> load(String name) {
        File file = new File(name); 
        
        if(!file.exists()) {
        	System.out.println(name + " does not exist!");
        	return null;
        }
        
        BufferedReader in = null;
        String input = null;
        ArrayList<AccountObject> accounts = new ArrayList<AccountObject>();
        
        try {
            in = new BufferedReader(new FileReader(file));

            try {
                while ((input = in.readLine()) != null) {
                    String[] split = input.split(":");
                    String email = split[0];
                    String password = split[1];
                    
                    accounts.add(new AccountObject(email, password));
                }
            } catch (IOException e) {  e.printStackTrace(); } finally { in.close(); }
        } catch (Exception e) {
        	e.printStackTrace();
        	
        }
        return accounts;
	}
	
	public static class AccountObject {
		
		public AccountObject(String email, String password) {
			this.email = email;
			this.password = password;
		}
		
		public String getEmail() {
			return email;
		}
		
		public void setEmail(String email) {
			this.email = email;
		}
		
		public String getPassword() {
			return password;
		}
		
		public void setPassword(String password) {
			this.password = password;
		}

		@Override
		public String toString() {
			return "AccountObject [email=" + email + ", password="
					+ password + "]";
		}

		private String email;
		private String password;	
	}

}
