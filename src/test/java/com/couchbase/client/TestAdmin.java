/**
 * Copyright (C) 2009-2011 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */
package com.couchbase.client;

import java.io.BufferedReader;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import sun.misc.BASE64Encoder;
/**
 * A TestAdmin Class.
 */
public final class TestAdmin {
  private static String serverName;
  private static String adminUserName;
  private static String adminUserPassword;
  private static String bucketName;
  private static String bucketPassword;

  protected TestAdmin(String serverName, String adminUserName,
          String adminUserPassword, String bucketName,
          String bucketPassword) {
    TestAdmin.serverName = serverName;
    TestAdmin.adminUserName = adminUserName;
    TestAdmin.adminUserPassword = adminUserPassword;
    TestAdmin.bucketName = bucketName;
    TestAdmin.bucketPassword = bucketPassword;
  }

  public static String executePost(URL targetURL,
         String urlParameters, String username,
         String password) throws IOException {
    HttpURLConnection connection = null;
    try {
      //Create connection
      connection = (HttpURLConnection)targetURL.openConnection();
      // write auth header
      BASE64Encoder encoder = new BASE64Encoder();
      String encodedCredential =
              encoder.encode((username + ":" + password).getBytes());
      connection.setRequestProperty("Authorization",
              "Basic " + encodedCredential);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type",
           "application/x-www-form-urlencoded");

      connection.setRequestProperty("Content-Length", ""
              + Integer.toString(urlParameters.getBytes().length));
      connection.setRequestProperty("Content-Language", "en-US");

      connection.setUseCaches(false);
      connection.setDoInput(true);
      connection.setDoOutput(true);

      //Send request
      DataOutputStream wr = new DataOutputStream(
                  connection.getOutputStream());
      wr.writeBytes(urlParameters);
      wr.flush();
      wr.close();

      //Get Response
      InputStream is = connection.getInputStream();
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      String line;
      StringBuilder response = new StringBuilder();
      while((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();
      return response.toString();

    } finally {
      if(connection != null) {
        connection.disconnect();
      }
    }
  }

  private static void request(boolean quiet, String method,
          URL url, String username, String password,
          InputStream body, String encodedRequest) throws IOException {
    // sigh.  openConnection() doesn't actually open the connection,
    // just gives you a URLConnection.
    // connect() will open the connection.
    if (!quiet) {
      System.out.println("[issuing request: " + method + " " + url + "]");
    }
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod(method);

    if (username != null) {
      // write auth header
      BASE64Encoder encoder = new BASE64Encoder();
      String encodedCredential =
              encoder.encode((username + ":" + password).getBytes());
      connection.setRequestProperty("Authorization",
              "Basic " + encodedCredential);
    }


    if (method.equals("POST")) {
      connection.setRequestProperty("Content-Type",
              "application/x-www-form-urlencoded");
      connection.setRequestProperty("Content-Length", ""
              + Integer.toString(encodedRequest.getBytes("UTF-8").length));
      connection.setRequestProperty("Content-Language", "en-US");
      connection.setUseCaches(false);
      connection.setDoInput(true);
      connection.setDoOutput(true);
    }

    // write body if we're doing POST or PUT
    byte[] buffer = new byte[8192];
    int read = 0;
    if (body != null) {
      connection.setDoOutput(true);

      OutputStream output = connection.getOutputStream();
      while ((read = body.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
    }

    // do request
    long time = System.currentTimeMillis();
    connection.connect();

    InputStream responseBodyStream = connection.getInputStream();
    StringBuffer responseBody = new StringBuffer();
    while ((read = responseBodyStream.read(buffer)) != -1) {
      responseBody.append(new String(buffer, 0, read));
    }
    connection.disconnect();
    time = System.currentTimeMillis() - time;

    // start printing output
    if (!quiet) {
      System.out.println("[read " + responseBody.length()
              + " chars in " + time + "ms]");
    }

    // look at headers
    // the 0th header has a null key, and the value is
    // the response line ("HTTP/1.1 200 OK" or whatever)
    if (!quiet) {
      String header = null;
      String headerValue = null;
      int index = 0;
      while ((headerValue = connection.getHeaderField(index)) != null) {
        header = connection.getHeaderFieldKey(index);

        if (header == null) {
          System.out.println(headerValue);
        } else {
          System.out.println(header + ": " + headerValue);
        }

        index++;
      }
      System.out.println("");
    }

    // dump body
    if (!quiet) {
      System.out.print(responseBody);
      System.out.flush();
    }
  }

  public static boolean doDeleteDefault() throws IOException {

    URL url = new URL("http://"
            + serverName
            + ":8091/pools/"
            + bucketName + "/buckets/" + bucketName);
    try {
      request(true, "DELETE", url,
              adminUserName, adminUserPassword, null, null);
    } catch (FileNotFoundException ex) {
      System.err.println("Warning: bucket doesn't exist, could"
              + "not be deleted.");
    }

    return true;

  }

  public static String doCreateDefault256() throws IOException {

    URL url = new URL("http://" + serverName
            + ":8091/pools/" + bucketName + "/buckets/");
    String args = "parallelDBAndViewCompaction=false&"
            + "autoCompactionDefined=false&"
            + "replicaIndex=0&"
            + "replicaNumber=1&"
            + "saslPassword=&"
            + "authType=sasl&"
            + "ramQuotaMB=256&"
            + "bucketType=membase&"
            + "name=" + bucketName;
    return (executePost(url, args, adminUserName, adminUserPassword));

  }
  public static void reCreateDefaultBucket() throws Exception {
    try {
      doDeleteDefault();
      Thread.sleep(2000);
      doCreateDefault256();
      Thread.sleep(8000);
    } catch (Exception e) {
      throw(e);
    }
  }
}
