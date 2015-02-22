/*
 * Copyright 2015 NEC Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.o3project.odenos.remoteobject.message;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.Unpacker;

import java.io.IOException;

/**
 * Request message.
 *
 */
public class Request extends MessageBodyUnpacker {

  private static final int MSG_NUM = 5;

  public String srcId = "unknown";
  public String objectId;
  public Method method;
  public String path;

  public enum Method {
    GET, PUT, POST, DELETE
  }

  /**
   * Constructor.
   * @deprecated {@link #Request(String, Method, String, Object)}.
   */
  @Deprecated
  public Request() {
  }

  /**
   * Constructor.
   * @param objectId object ID.
   * @param method a method.
   * @param path a path.
   * @param body contents.
   */
  public Request(String objectId, Method method, String path, Object body) {
    this.objectId = objectId;
    this.method = method;
    this.path = path;
    this.body = body;
  }

  /**
   * Constructor.
   * @param srcId src object ID. (Logic)
   * @param dstId dst object ID. (Network)
   * @param method a method.
   * @param path a path.
   * @param body contents.
   */
  public Request(String srcId, String dstId, Method method, String path, Object body) {
    this.srcId = srcId;
    this.objectId = dstId;
    this.method = method;
    this.path = path;
    this.body = body;
  }

  @Override
  public void readFrom(Unpacker unpacker) throws IOException {
    unpacker.readArrayBegin();
    srcId = unpacker.readString();
    objectId = unpacker.readString();
    method = Method.valueOf(unpacker.readString());
    path = unpacker.readString();
    bodyValue = unpacker.readValue();
    unpacker.readArrayEnd();
  }

  @Override
  public void writeTo(Packer packer) throws IOException {
    packer.writeArrayBegin(MSG_NUM);
    packer.write(srcId);
    packer.write(objectId);
    packer.write(method.name());
    packer.write(path);
    if (bodyValue != null) {
      packer.write(bodyValue);
    } else {
      packer.write(body);
    }
    packer.writeArrayEnd();
  }

  /*
   * (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {

    ToStringBuilder sb = new ToStringBuilder(this);

    sb.append("srcId", srcId);
    sb.append("objectId", objectId);
    sb.append("method", method);
    sb.append("path", path);
    sb.append("body", body);

    return sb.toString();
  }
}
