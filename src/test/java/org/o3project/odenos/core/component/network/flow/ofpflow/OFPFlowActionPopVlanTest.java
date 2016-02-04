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

package org.o3project.odenos.core.component.network.flow.ofpflow;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.msgpack.type.Value;

import java.util.HashMap;
import java.util.Map;

public class OFPFlowActionPopVlanTest {

  private OFPFlowActionPopVlan target;

  /**
   * @throws java.lang.Exception throws Exception in targets
   */
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  /**
   * @throws java.lang.Exception throws Exception in targets
   */
  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  /**
   * @throws java.lang.Exception throws Exception in targets
   */
  @Before
  public void setUp() throws Exception {
    target = spy(new OFPFlowActionPopVlan());
  }

  /**
   * @throws java.lang.Exception throws Exception in targets
   */
  @After
  public void tearDown() throws Exception {
    target = null;
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#validate()}
   * .
   */
  @Test
  public final void testValidate() {
    assertThat(target.validate(), is(true));
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#getType()}
   * .
   */
  @Test
  public final void testGetType() {
    assertThat(target.getType(), is("OFPFlowActionPopVlan"));
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#readValue(org.msgpack.type.Value)}
   * .
   */
  @Test
  public final void testReadValue() {
    Value value = Mockito.mock(Value.class);
    assertThat(target.readValue(value), is(true));
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#writeValueSub(java.util.Map)}
   * .
   */
  @Test
  public final void testWriteValueSub() {
    /*
     * set
     */
    Map<String, Value> map = new HashMap<String, Value>();

    /*
     * test
     */
    boolean result = target.writeValueSub(map);

    /*
     * check
     */
    assertThat(result, is(true));
    assertThat(map.get("type").toString(), is("\"OFPFlowActionPopVlan\""));
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#equals(java.lang.Object)}
   * .
   */
  @Test
  public final void testEqualsObject() {
    boolean result = target.equals(target);

    assertThat(result, is(true));
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#equals(java.lang.Object)}
   * .
   */
  @Test
  public final void testEqualsObjectNull() {
    boolean result = target.equals(null);

    assertThat(result, is(false));
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#equals(java.lang.Object)}
   * .
   */
  @Test
  public final void testEqualsObjectNotOFPFlowActionPopVlan() {
    boolean result = target.equals("String");

    assertThat(result, is(false));
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#equals(java.lang.Object)}
   * .
   */
  @Test
  public final void testEqualsObjectSuperFalse() {
    /*
     * set
     */
    OFPFlowActionPopVlan value = new OFPFlowActionPopVlan();
    doReturn("type").when(target).getType();

    /*
     * test
     */
    boolean result = target.equals(value);

    /*
     * check
     */
    assertThat(result, is(false));
  }

  /**
   * Test method for
   * {@link org.o3project.odenos.core.component.network.flow.ofpflow.OFPFlowActionPopVlan#toString()}
   * .
   */
  @Test
  public final void testToString() {

    /*
     * setting
     */
    target = new OFPFlowActionPopVlan();

    /*
     * test
     */
    String result = target.toString();

    /*
     * check
     */
    assertThat(result.endsWith(target.getType()), is(true));
  }

}
