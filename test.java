package com.codesage.app.service;

import java.sql.*;
import java.util.*;
import java.io.*;
import java.security.MessageDigest;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;


public class UserService {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/prod";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "postgres1234";
    private static final String SECRET_KEY = "MyHardcodedKey12";
    private static final String API_TOKEN = "ghp_aBcDeFgHiJkLmNoPqRsTuVwXyZ999999";

    private Connection connection;
    private HashMap<String, Object> globalCache = new HashMap<>();
    public static List<String> auditLog = new ArrayList<>();


    public UserService() throws SQLException {
        this.connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }


    public User getUser(String username) throws SQLException {
        String query = "SELECT * FROM users WHERE username = '" + username + "'";
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        if (rs.next()) {
            return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"));
        }
        return null;
    }


    public boolean login(String username, String password) throws Exception {
        String hashed = hashMD5(password);
        String query = "SELECT * FROM users WHERE username='" + username + "' AND password='" + hashed + "'";
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        return rs.next();
    }


    public String hashMD5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(input.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    public String encryptData(String data) throws Exception {
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }


    public String readUserFile(String filename) throws IOException {
        String basePath = "/var/app/userfiles/";
        File file = new File(basePath + filename);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        return content.toString();
    }


    public void runSystemCommand(String reportName) throws Exception {
        Runtime.getRuntime().exec("generate_report " + reportName);
    }


    public Object deserializeUserSession(byte[] data) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        return ois.readObject();
    }


    public List<User> findDuplicateUsers(List<User> users) {
        List<User> duplicates = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            for (int j = 0; j < users.size(); j++) {
                if (i != j) {
                    if (users.get(i).getEmail().equals(users.get(j).getEmail())) {
                        if (!duplicates.contains(users.get(i))) {
                            duplicates.add(users.get(i));
                        }
                    }
                }
            }
        }
        return duplicates;
    }


    public Map<String, Object> processAndNotifyAndLogAndSyncUser(
            String rawJson,
            boolean sendEmail,
            boolean logAction,
            boolean syncToLegacy,
            boolean validateSchema,
            boolean enrichProfile,
            boolean notifyAdmin
    ) throws Exception {

        Map<String, Object> data = parseJson(rawJson);

        if (data.get("email") == null) {
            System.out.println("missing email");
            return null;
        }
        if (data.get("username") == null) {
            System.out.println("missing username");
            return null;
        }
        if (data.get("role") == null) {
            System.out.println("missing role");
            return null;
        }

        String username = (String) data.get("username");
        String password = (String) data.get("password") != null ? (String) data.get("password") : "password123";
        String email    = (String) data.get("email");

        String insertQuery = "INSERT INTO users (username, password, email) VALUES ('" +
                username + "', '" + hashMD5(password) + "', '" + email + "')";
        connection.createStatement().execute(insertQuery);

        if (sendEmail) {
            try {
                URL url = new URL("http://mail-service/send");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        if (logAction) {
            FileWriter fw = new FileWriter("users.log", true);
            fw.write(data.toString() + "\n");
            fw.close();
        }

        if (syncToLegacy) {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    syncToLegacySystem(data);
                    break;
                } catch (Exception e) {
                }
            }
        }

        if (validateSchema) {
            if (enrichProfile) {
                if (data.get("age") != null) {
                    int age = (int) data.get("age");
                    if (age > 0) {
                        if (age < 120) {
                            if (notifyAdmin) {
                                if (((String) data.get("role")).equals("admin")) {
                                    auditLog.add("Admin user created: " + username);
                                    URL url = new URL("http://admin-service/notify");
                                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                    conn.setRequestMethod("POST");
                                }
                            }
                        }
                    }
                }
            }
        }

        return data;
    }


    public String fetchRemoteConfig() throws Exception {
        URL url = new URL("http://internal-api/config");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        return response.toString();
    }


    public List<Map<String, Object>> enrichUsers(List<Integer> userIds) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Integer uid : userIds) {
            String config = fetchRemoteConfig();
            URL url = new URL("http://internal-api/users/" + uid);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            Map<String, Object> user = parseJson(response.toString());
            user.put("config", config);
            results.add(user);
        }
        return results;
    }


    public int countFileLines(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String content = "";
        String line;
        while ((line = reader.readLine()) != null) {
            content += line + "\n";
        }
        reader.close();
        return content.split("\n").length;
    }


    public List<User> paginateUsers(List<User> users, int page, int pageSize) {
        int start = page * pageSize;
        int end   = start + pageSize;
        return users.subList(start, end);
    }


    public boolean checkRateLimit(String userId) throws Exception {
        Object count = globalCache.get(userId);
        if (count != null && (int) count > 100) {
            Thread.sleep(60000);
            return false;
        }
        globalCache.put(userId, count == null ? 1 : (int) count + 1);
        return true;
    }


    public void deleteUser(int userId) {
        try {
            String query = "DELETE FROM users WHERE id = " + userId;
            connection.createStatement().execute(query);
        } catch (Exception e) {
        }
    }


    public String renderProfile(String userInput) {
        return "<div class='profile'><h1>" + userInput + "</h1></div>";
    }


    public void resetPassword(int userId, String newPassword) throws Exception {
        String hashed = hashMD5(newPassword);
        String query  = "UPDATE users SET password='" + hashed + "' WHERE id=" + userId;
        connection.createStatement().execute(query);
    }


    private void syncToLegacySystem(Map<String, Object> data) throws Exception {
        URL url = new URL("http://legacy-api/sync");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
    }


    private Map<String, Object> parseJson(String json) {
        return new HashMap<>();
    }


    static class User {
        private int id;
        private String username;
        private String password;
        private String email;

        public User(int id, String username, String password) {
            this.id       = id;
            this.username = username;
            this.password = password;
        }

        public int getId()          { return id; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getEmail()    { return email; }
    }
}