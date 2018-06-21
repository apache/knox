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
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.JsonUtils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import javax.annotation.PostConstruct;
import javax.security.auth.Subject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.security.AccessController;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(S3BucketsResource.RESOURCE_PATH)
public class S3BucketsResource {
  private static final String BUCKETS_API_PATH = "buckets";
  private static final String BUCKET_API_PATH = "buckets/{id}";
  private static final String OBJECT_API_PATH = "buckets/{bucket}/{id}";
  private static final String OBJECTS_API_PATH = "buckets/{bucket}";
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
      props.setProperty(paramName, context.getInitParameter(paramName));
    }
    
    return props;
  }

  @DELETE
  @Consumes({APPLICATION_XML, APPLICATION_JSON, TEXT_PLAIN})
  @Produces({APPLICATION_JSON})
  @Path(OBJECT_API_PATH)
  public Response deleteObject(@PathParam("bucket") String bucket,
      @PathParam("id") String id,
      @Context HttpHeaders headers) {
    return getDeleteObjectResponse(bucket, id);
  }

  private Response getDeleteObjectResponse(String bucket, String id) {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    PrintWriter writer = null;
    try {
      writer = response.getWriter();
      doDeleteObject(bucket, id);
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

  private void doDeleteObject(String bucket, String id) {
    AmazonS3 s3 = getS3Client();
    s3.deleteObject(bucket, id);
  }

  @PUT
  @Consumes({APPLICATION_XML, APPLICATION_JSON, TEXT_PLAIN})
  @Produces({APPLICATION_JSON})
  @Path(OBJECT_API_PATH)
  public Response createObject(@PathParam("bucket") String bucket,
      @PathParam("id") String id,
      @Context HttpHeaders headers, String content) {
    return getCreateObjectResponse(bucket, id, content);
  }

  private Response getCreateObjectResponse(String bucket, String id, String content) {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    PrintWriter writer = null;
    try {
      writer = response.getWriter();
      doCreateObject(bucket, id, content);
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

  private void doCreateObject(String bucket, String id, String content) {
    AmazonS3 s3 = getS3Client();
    s3.putObject(bucket, id, content);
  }

  @PUT
  @Produces({APPLICATION_JSON})
  @Path(BUCKET_API_PATH)
  public Response createBucket(@PathParam("id") String id) {
    return getCreateBucketResponse(id);
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path(OBJECTS_API_PATH)
  public Response listObjects(@PathParam("bucket") String bucket) {
    // use this to get to idbroker service once moved there
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    return getListObjectsResponse(bucket);
  }

  private Response getListObjectsResponse(String bucket) {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    PrintWriter writer = null;
    try {
      writer = response.getWriter();
      writer.println(doGetObjects(bucket));
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

  private String doGetObjects(String bucket) {
    AmazonS3 s3 = getS3Client();
    Map<String, Object> objectMap = new HashMap<String, Object>();
    ObjectListing objectListing = s3.listObjects(new ListObjectsRequest()
        .withBucketName(bucket));
    for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
      objectMap.put(objectSummary.getKey(), objectSummary);
    }
    return JsonUtils.renderAsJsonString(objectMap);
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path(OBJECT_API_PATH)
  public Response listObjects(@PathParam("bucket") String bucket,
      @PathParam("id") String id) {
    return getObjectResponse(bucket, id);
  }

  private Response getObjectResponse(String bucket, String id) {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);

    S3Object o = doGetObject(bucket, id);
    BufferedReader reader = new BufferedReader(new InputStreamReader(o.getObjectContent()));
    StreamingOutput stream = new StreamingOutput() {
      @Override
      public void write(OutputStream os) throws IOException,
      WebApplicationException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(os));
        while (true) {
          String line = reader.readLine();
          if (line == null) break;
          writer.write(line);
        }
        writer.flush();
      }
    };
    return Response.ok(stream).build();
  }

  private S3Object doGetObject(String bucket, String id) {
    AmazonS3 s3 = getS3Client();
    S3Object object = s3.getObject(new GetObjectRequest(bucket, id));
//    System.out.println("Content-Type: "  + object.getObjectMetadata().getContentType());
    return object;
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
    AmazonS3 s3 = getS3Client();
    s3.createBucket(id);
  }

  @DELETE
  @Produces({APPLICATION_JSON})
  @Path(BUCKET_API_PATH)
  public Response deleteBucket(@PathParam("id") String id) {
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
    AmazonS3 s3 = getS3Client();
    s3.deleteBucket(id);
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path(BUCKETS_API_PATH)
  public Response listBuckets() {
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
    AmazonS3 s3 = getS3Client();
    Map<String, Object> bucketMap = new HashMap<String, Object>();
    List<Bucket> buckets = s3.listBuckets();
    for (Bucket bucket : buckets) {
      bucketMap.put(bucket.getName(), bucket);
    }
    return JsonUtils.renderAsJsonString(bucketMap);
  }

  protected AmazonS3 getS3Client() {
    HashMap<String, AmazonS3> registry = (HashMap<String, AmazonS3>)
        context.getAttribute("s3.client.registry");

    if (registry == null) {
      registry = new HashMap<String, AmazonS3>();
      context.setAttribute("s3.client.registry", registry);
    }
    Subject subject = Subject.getSubject(AccessController.getContext());
    String username = getEffectiveUserName(subject);
    AmazonS3 s3 = registry.get(username);
    if (s3 == null || expired(s3)) {
      s3 = s3b.getS3Client();
      registry.put(username, s3);
    }
    return s3;
  }

  private boolean expired(AmazonS3 s3) {
    // TODO: change to registering a wrapper that keeps track of expiration time
    // return true when a new client should be create for the effective user name
    return false;
  }

  private String getEffectiveUserName(Subject subject) {
    return SubjectUtils.getEffectivePrincipalName(subject);
  }

}
