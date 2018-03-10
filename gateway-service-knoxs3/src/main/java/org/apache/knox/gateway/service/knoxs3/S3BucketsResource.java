/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.service.knoxs3;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.JsonUtils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;


@Path(S3BucketsResource.RESOURCE_PATH)
public class S3BucketsResource {
  private static final String BUCKETS_API_PATH = "buckets";
  private static final String BUCKET_API_PATH = "buckets/{id}";
  private static KnoxS3ServiceMessages log = MessagesFactory.get(KnoxS3ServiceMessages.class);
  private static final String VERSION_TAG = "v1";
  static final String RESOURCE_PATH = "/knoxs3/" + VERSION_TAG;

  private static final String CONTENT_TYPE = "application/json";
  private static final String CACHE_CONTROL = "Cache-Control";
  private static final String NO_CACHE = "must-revalidate,no-cache,no-store";

  private KnoxS3ClientBuilder s3b = new KnoxS3ClientBuilder();

  @Context
  HttpServletRequest request;

  @Context
  private HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {
    s3b.init(getProperties());
  }

  private Properties getProperties() {
    Properties props = new Properties();
    String paramName = null;
    Enumeration<String> e = context.getInitParameterNames();
    while (e.hasMoreElements()) {
      paramName = (String)e.nextElement();
      if (paramName.startsWith("s3.")) {
        props.setProperty(paramName, context.getInitParameter(paramName));
      }
    }
    
    return props;
  }

  @PUT
  @Produces({APPLICATION_JSON})
  @Path(BUCKET_API_PATH)
  public Response createBucket(@PathParam("id") String id) {
    // use this to get to idbroker service once moved there
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    return getCreateBucketResponse(id);
  }

  private Response getCreateBucketResponse(String id) {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    PrintWriter writer = null;
    try {
      writer = response.getWriter();
      doCreateBucket(id);
    } catch (Exception ioe) {
      log.logException("create", ioe);
      return Response.serverError().entity(String.format("Failed to reply correctly due to : %s ", ioe)).build();
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
    return Response.ok().build();
  }

  private void doCreateBucket(String id) {
    AmazonS3 s3 = s3b.getS3Client();
    s3.createBucket(id);
  }

  @DELETE
  @Produces({APPLICATION_JSON})
  @Path(BUCKET_API_PATH)
  public Response deleteBucket(@PathParam("id") String id) {
    // use this to get to idbroker service once moved there
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    return getDeleteBucketResponse(id);

  }

  private Response getDeleteBucketResponse(String id) {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    PrintWriter writer = null;
    try {
      writer = response.getWriter();
      doDeleteBucket(id);
    } catch (Exception ioe) {
      log.logException("delete", ioe);
      return Response.serverError().entity(String.format("Failed to reply correctly due to : %s ", ioe)).build();
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
    return Response.ok().build();
  }

  private void doDeleteBucket(String id) {
    AmazonS3 s3 = s3b.getS3Client();
    s3.deleteBucket(id);
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path(BUCKETS_API_PATH)
  public Response listBuckets() {
    // use this to get to idbroker service once moved there
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    return getListBucketsResponse();
  }

  private Response getListBucketsResponse() {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    PrintWriter writer = null;
    try {
      writer = response.getWriter();
      writer.println(getBuckets());
    } catch (Exception e) {
      log.logException("list", e);
      return Response.serverError().entity(String.format("Failed to reply correctly due to : %s ", e)).build();
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
    return Response.ok().build();
  }

  private String getBuckets() {
    AmazonS3 s3 = s3b.getS3Client();
    Map<String, Object> bucketMap = new HashMap<String, Object>();
    List<Bucket> buckets = s3.listBuckets();
    for (Bucket bucket : buckets) {
      System.out.println(bucket.getName());
      bucketMap.put(bucket.getName(), bucket);
    }
    return JsonUtils.renderAsJsonString(bucketMap);
  }

}
