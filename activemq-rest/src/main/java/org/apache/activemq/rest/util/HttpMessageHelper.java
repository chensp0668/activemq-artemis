/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.rest.util;

import org.apache.activemq.api.core.SimpleString;
import org.apache.activemq.api.core.client.ClientMessage;
import org.apache.activemq.rest.ActiveMQRestLogger;
import org.apache.activemq.rest.HttpHeaderProperty;
import org.jboss.resteasy.client.ClientRequest;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Map.Entry;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class HttpMessageHelper
{
   public static final String POSTED_AS_HTTP_MESSAGE = "postedAsHttpMessage";

   public static boolean isTransferableHttpHeader(String key)
   {
      String lowerKey = key.toLowerCase();
      return lowerKey.toLowerCase().startsWith("content") || lowerKey.toLowerCase().equals("link");
   }

   public static void buildMessage(ClientMessage message, Response.ResponseBuilder builder)
   {
      for (SimpleString key : message.getPropertyNames())
      {
         String k = key.toString();
         String headerName = HttpHeaderProperty.fromPropertyName(k);
         if (headerName == null)
         {
            continue;
         }
         builder.header(headerName, message.getStringProperty(k));
      }
      int size = message.getBodySize();
      if (size > 0)
      {
         byte[] body = new byte[size];
         message.getBodyBuffer().readBytes(body);
         Boolean aBoolean = message.getBooleanProperty(POSTED_AS_HTTP_MESSAGE);
         if (aBoolean != null && aBoolean.booleanValue())
         {
            builder.entity(body);
         }
         else
         {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            Object obj = null;
            try
            {
               ObjectInputStream ois = new ObjectInputStream(bais);
               obj = ois.readObject();
            }
            catch (Exception e)
            {
               throw new RuntimeException(e);
            }
            builder.entity(obj);
         }
      }
   }

   public static void buildMessage(ClientMessage message, ClientRequest request, String contentType)
   {
      for (SimpleString key : message.getPropertyNames())
      {
         String k = key.toString();
         String headerName = HttpHeaderProperty.fromPropertyName(k);
         if (headerName == null || headerName.contains("content-length"))
         {
            continue;
         }
         String value = message.getStringProperty(k);

         request.header(headerName, value);
         ActiveMQRestLogger.LOGGER.debug("Examining " + headerName + ": " + value);
         // override default content type if it is set as a message property
         if (headerName.equalsIgnoreCase("content-type"))
         {
            contentType = value;
            ActiveMQRestLogger.LOGGER.debug("Using contentType: " + contentType);
         }
      }
      int size = message.getBodySize();
      if (size > 0)
      {
         Boolean aBoolean = message.getBooleanProperty(POSTED_AS_HTTP_MESSAGE);
         if (aBoolean != null && aBoolean.booleanValue())
         {
            byte[] body = new byte[size];
            message.getBodyBuffer().readBytes(body);
            ActiveMQRestLogger.LOGGER.debug("Building Message from HTTP message");
            request.body(contentType, body);
         }
         else
         {
            // assume posted as a JMS or ActiveMQ object message
            size = message.getBodyBuffer().readInt();
            byte[] body = new byte[size];
            message.getBodyBuffer().readBytes(body);
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            Object obj = null;
            try
            {
               ObjectInputStream ois = new ObjectInputStream(bais);
               obj = ois.readObject();
               ActiveMQRestLogger.LOGGER.debug("**** Building Message from object: " + obj.toString());
               request.body(contentType, obj);
            }
            catch (Exception e)
            {
               throw new RuntimeException(e);
            }
         }
      }
   }

   public static void writeHttpMessage(HttpHeaders headers, byte[] body, ClientMessage message) throws Exception
   {

      MultivaluedMap<String, String> hdrs = headers.getRequestHeaders();
      for (Entry<String, List<String>> entry : hdrs.entrySet())
      {
         String key = entry.getKey();
         if (isTransferableHttpHeader(key))
         {
            List<String> vals = entry.getValue();
            String value = concatenateHeaderValue(vals);
            message.putStringProperty(HttpHeaderProperty.toPropertyName(key), value);
         }
      }
      message.putBooleanProperty(POSTED_AS_HTTP_MESSAGE, true);
      message.getBodyBuffer().writeBytes(body);
   }

   public static String concatenateHeaderValue(List<String> vals)
   {
      if (vals == null)
      {
         return "";
      }
      StringBuilder val = new StringBuilder();
      for (String v : vals)
      {
         if (val.length() > 0)
         {
            val.append(",");
         }
         val.append(v);
      }
      return val.toString();
   }
}