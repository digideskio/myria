package edu.washington.escience.myriad.api;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import edu.washington.escience.myriad.DbException;
import edu.washington.escience.myriad.RelationKey;
import edu.washington.escience.myriad.Schema;
import edu.washington.escience.myriad.api.encoding.DatasetEncoding;
import edu.washington.escience.myriad.api.encoding.TipsyDatasetEncoding;
import edu.washington.escience.myriad.coordinator.catalog.CatalogException;
import edu.washington.escience.myriad.operator.FileScan;
import edu.washington.escience.myriad.operator.Operator;
import edu.washington.escience.myriad.operator.TipsyFileScan;

/**
 * This is the class that handles API calls that return relation schemas.
 * 
 * @author dhalperi
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset")
public final class DatasetResource {
  /**
   * @param userName the user who owns the target relation.
   * @param programName the program to which the target relation belongs.
   * @param relationName the name of the target relation.
   * @return the schema of the specified relation.
   * @throws CatalogException if there is an error in the database.
   */
  @GET
  @Path("/user-{userName}/program-{programName}/relation-{relationName}")
  public Schema getDataset(@PathParam("userName") final String userName,
      @PathParam("programName") final String programName, @PathParam("relationName") final String relationName)
      throws CatalogException {
    Schema schema = MyriaApiUtils.getServer().getSchema(RelationKey.of(userName, programName, relationName));
    if (schema == null) {
      /* Not found, throw a 404 (Not Found) */
      throw new MyriaApiException(Status.NOT_FOUND, "That dataset was not found");
    }
    /* Yay, worked! */
    return schema;
  }

  /**
   * @param payload the request payload.
   * @param uriInfo information about the current URL.
   * @return the created dataset resource.
   * @throws DbException if there is an error in the database.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response newDataset(final byte[] payload, @Context final UriInfo uriInfo) throws DbException {
    /* Attempt to deserialize the object. */
    DatasetEncoding dataset = MyriaApiUtils.deserialize(payload, DatasetEncoding.class);

    /* If we already have a dataset by this name, tell the user there's a conflict. */
    try {
      if (MyriaApiUtils.getServer().getSchema(dataset.relationKey) != null) {
        /* Found, throw a 409 (Conflict) */
        throw new MyriaApiException(Status.CONFLICT, "That dataset already exists.");
      }
    } catch (CatalogException e) {
      throw new DbException(e);
    }

    /* If we don't have any workers alive right now, tell the user we're busy. */
    Set<Integer> workers = MyriaApiUtils.getServer().getAliveWorkers();
    if (workers.size() == 0) {
      /* Throw a 503 (Service Unavailable) */
      throw new MyriaApiException(Status.SERVICE_UNAVAILABLE, "There are no alive workers to receive this dataset.");
    }

    /* Moreover, check whether all requested workers are alive. */
    if (dataset.workers != null && !MyriaApiUtils.getServer().getAliveWorkers().containsAll(dataset.workers)) {
      /* Throw a 503 (Service Unavailable) */
      throw new MyriaApiException(Status.SERVICE_UNAVAILABLE, "Not all requested workers are alive");
    }

    /* Do the work. */
    Operator source;
    if (dataset.data != null) {
      source = new FileScan(dataset.schema);
      ((FileScan) source).setInputStream(new ByteArrayInputStream(dataset.data));
    } else {
      try {
        source = new FileScan(dataset.fileName, dataset.schema);
      } catch (FileNotFoundException e) {
        throw new MyriaApiException(Status.NOT_FOUND, e);
      }
    }
    try {
      MyriaApiUtils.getServer().ingestDataset(dataset.relationKey, dataset.workers, source);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    /* In the response, tell the client the path to the relation. */
    UriBuilder queryUri = uriInfo.getBaseUriBuilder();
    return Response.created(
        queryUri.path("dataset").path("user-" + dataset.relationKey.getUserName()).path(
            "program-" + dataset.relationKey.getProgramName())
            .path("relation-" + dataset.relationKey.getRelationName()).build()).build();
  }

  /**
   * @param payload the request payload.
   * @param uriInfo information about the current URL.
   * @return the created dataset resource.
   * @throws DbException if there is an error in the database.
   */
  @POST
  @Path("/tipsy")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response newTipsyDataset(final byte[] payload, @Context final UriInfo uriInfo) throws DbException {
    /* Attempt to deserialize the object. */
    TipsyDatasetEncoding dataset = MyriaApiUtils.deserialize(payload, TipsyDatasetEncoding.class);

    /* If we already have a dataset by this name, tell the user there's a conflict. */
    try {
      if (MyriaApiUtils.getServer().getSchema(dataset.relationKey) != null) {
        /* Found, throw a 409 (Conflict) */
        throw new MyriaApiException(Status.CONFLICT, "That dataset already exists.");
      }
    } catch (CatalogException e) {
      throw new DbException(e);
    }

    /* If we don't have any workers alive right now, tell the user we're busy. */
    Set<Integer> workers = MyriaApiUtils.getServer().getAliveWorkers();
    if (workers.size() == 0) {
      /* Throw a 503 (Service Unavailable) */
      throw new MyriaApiException(Status.SERVICE_UNAVAILABLE, "There are no alive workers to receive this dataset.");
    }

    /* Moreover, check whether all requested workers are alive. */
    if (dataset.workers != null && !MyriaApiUtils.getServer().getAliveWorkers().containsAll(dataset.workers)) {
      /* Throw a 503 (Service Unavailable) */
      throw new MyriaApiException(Status.SERVICE_UNAVAILABLE, "Not all requested workers are alive");
    }

    /* Do the work. */
    try {
      MyriaApiUtils.getServer().ingestDataset(dataset.relationKey, dataset.workers,
          new TipsyFileScan(dataset.tipsyFilename, dataset.grpFilename, dataset.iorderFilename, true));
    } catch (InterruptedException ee) {
      Thread.currentThread().interrupt();
      ee.printStackTrace();
    }

    /* In the response, tell the client the path to the relation. */
    UriBuilder queryUri = uriInfo.getBaseUriBuilder();
    return Response.created(
        queryUri.path("dataset").path("user-" + dataset.relationKey.getUserName()).path(
            "program-" + dataset.relationKey.getProgramName())
            .path("relation-" + dataset.relationKey.getRelationName()).build()).build();
  }
}
