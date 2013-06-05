package edu.washington.escience.myriad.api.encoding;

import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonSubTypes.Type;
import org.codehaus.jackson.annotate.JsonTypeInfo;

import com.google.common.base.Preconditions;

import edu.washington.escience.myriad.api.MyriaApiException;
import edu.washington.escience.myriad.operator.apply.IFunction;

/**
 * 
 * @author leelee
 * 
 * @param <T>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({ @Type(value = ConstantMultiplicationIFunctionEncoding.class, name = "ConstantMultiplicationIFunction") })
public abstract class IFunctionEncoding<T extends IFunction> implements MyriaApiEncoding {
  public String type;

  @Override
  public void validate() throws MyriaApiException {
    try {
      Preconditions.checkNotNull(type);
    } catch (Exception e) {
      throw new MyriaApiException(Status.BAD_REQUEST, "required field: type");
    }
  }

  public abstract T construct();
}