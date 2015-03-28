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

package org.o3project.odenos.component.federator;

import org.o3project.odenos.core.component.Logic;
import org.o3project.odenos.core.component.ConversionTable;
import org.o3project.odenos.core.component.NetworkInterface;
import org.o3project.odenos.core.component.network.flow.Flow;
import org.o3project.odenos.core.component.network.flow.FlowObject;
import org.o3project.odenos.core.component.network.flow.basic.BasicFlow;
import org.o3project.odenos.core.component.network.flow.basic.BasicFlowMatch;
import org.o3project.odenos.core.component.network.flow.basic.FlowAction;
import org.o3project.odenos.core.component.network.flow.basic.FlowActionOutput;
import org.o3project.odenos.core.component.network.topology.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dispose onFlow in Federator class.
 */
public class FederatorOnFlow {
  private static final Logger logger = LoggerFactory.getLogger(Federator.class);

  protected ConversionTable conversionTable;
  protected Map<String, NetworkInterface> networkInterfaces;

  /** Map of flows. key: networkId, value: src boundary */
  protected Map<String, BasicFlow> orgFlowList = new HashMap<>();

  /**
   * Constructors.
   * @param conversionTable specified conversion table.
   * @param networkInterfaces specified network interface.
   */
  public FederatorOnFlow(ConversionTable conversionTable,
      Map<String, NetworkInterface> networkInterfaces ) {

    this.conversionTable = conversionTable;
    this.networkInterfaces = networkInterfaces;
  }

  /**
   * Add flow with path.
   * @param networkId ID for networks.
   * @param flow BasicFlow.
   */
  public void flowAddedExistPath(String networkId, BasicFlow flow) {
    logger.debug("");
    // set orgFlowList
    doFlowAddedSelect(flow);
  }

  /**
   * Add flow without path.
   * @param networkId ID for networks.
   * @param flow BasicFlow.
   */
  public void flowAddedNotExistPath(String networkId, BasicFlow flow) {
    logger.debug("");

    
    BasicFlowMatch flowMatch = flow.getMatches().get(0);
    BasicFlow orgFlow = createOriginalFlowNotExistPath(networkId, flow);
    if (flowMatch == null || orgFlow == null) {
      logger.warn("invalid federated flow.");
      return;
    }

    String orgNodes = getConvNodeId(networkId, flowMatch.getInNode());
    if (orgNodes == null) {
      return;
    }
    String[] orgNodeList = orgNodes.split(Federator.SEPARATOR);
    String orgNwId = orgNodeList[0];
    NetworkInterface networkInterface = networkInterfaces.get(orgNwId);

    String flowId = flow.getFlowId();
    // PUT flow
    networkInterface.putFlow(orgFlow);

    // update conversionTable.
    conversionTable.addEntryFlow(
        networkId, flowId, orgNwId, flowId);
  }

  /**
   * Update flow when status is failed.
   * @param networkId ID for networks.
   * @param flow BasicFlow
   */
  public void flowUpdatePreStatusFailed(String networkId, BasicFlow flow) {
    logger.debug("");

    String fedNwId = getNetworkIdByType(Federator.FEDERATED_NETWORK);
    NetworkInterface fedNwIf = networkInterfaces.get(fedNwId);
    Flow fedFlow = fedNwIf.getFlow(getConvFlowId(networkId, flow.getFlowId()));
    if (fedFlow != null) {
      // set failed.
      fedFlow.setStatus(FlowObject.FlowStatus.FAILED.toString());
      // PUT flow.
      fedNwIf.putFlow(fedFlow);
    }

    List<String> orgNetworks =
        conversionTable.getConnectionList(Federator.ORIGINAL_NETWORK);
    for (String orgNwId : orgNetworks) {
      // update conversionTable.
      conversionTable.delEntryFlow(orgNwId, flow.getFlowId());

      NetworkInterface orgNwIf = networkInterfaces.get(orgNwId);
      // DELETE flow
      orgNwIf.delFlow(flow.getFlowId());
    }
  }

  /**
   * Update flow when status is established.
   * @param networkId ID for networks.
   * @param flow BasicFlow.
   * @return boolean true:status established. false:status isn't established.
   */
  public boolean flowUpdatePreStatusEstablished(String networkId, BasicFlow flow) {
    logger.debug("");

    List<String> orgNetworks =
        conversionTable.getConnectionList(Federator.ORIGINAL_NETWORK);
    for (String orgNwId : orgNetworks) {
      if (orgNwId.equals(networkId)) {
        continue;
      }
      if (conversionTable.getFlow(orgNwId, flow.getFlowId()).size() == 0) {
        continue;
      }
      NetworkInterface orgNwIf = networkInterfaces.get(orgNwId);
      Flow orgFlow = orgNwIf.getFlow(flow.getFlowId());

      if (!FlowObject.FlowStatus.ESTABLISHED.toString().equalsIgnoreCase(
          orgFlow.getStatus())) {
        logger.debug("not flow's status established.");
        return false;
      }
    }
    logger.debug("next federate stauts:: established.");
    return true;
  }

  /**
   * Update flow when status is none.
   * @param networkId ID for networks.
   * @param flow BasicFlow.
   * @return boolean true:status none. false:status isn't none.
   */
  public boolean flowUpdatePreStatusNone(String networkId, BasicFlow flow) {
    logger.debug("");

    List<String> orgNetworks =
        conversionTable.getConnectionList(Federator.ORIGINAL_NETWORK);
    for (String orgNwId : orgNetworks) {
      if (orgNwId.equals(networkId)) {
        continue;
      }
      if (conversionTable.getFlow(orgNwId, flow.getFlowId()).size() == 0) {
        continue;
      }
      NetworkInterface orgNwIf = networkInterfaces.get(orgNwId);
      Flow orgFlow = orgNwIf.getFlow(flow.getFlowId());

      if (!FlowObject.FlowStatus.NONE.toString().equalsIgnoreCase(
          orgFlow.getStatus())) {
        logger.debug("not flow's status none.");
        return false;
      }
    }
    logger.debug("next federate stauts:: none");
    return true;
  }
  
  /**
   * Update flow with path.
   * @param networkId ID for networks.
   * @param flow BasicFlow.
   */
  public void flowUpdateFromOriginal(String networkId, BasicFlow flow) {
    logger.debug("");
    
    BasicFlowMatch flowMatch = flow.getMatches().get(0);
    if (flowMatch == null) {
      logger.warn("invalid federated flow.");
      return;
    }

    // get federated network interface.
    String orgNodes = getConvNodeId(networkId, flowMatch.getInNode());
    if (orgNodes == null) {
      return;
    }
    String[] fedNodeList = orgNodes.split(Federator.SEPARATOR);
    String fedNwId = fedNodeList[0];
    NetworkInterface fedNwIf = networkInterfaces.get(fedNwId);

    String flowId = flow.getFlowId();
    // Get Flow. and set version.
    Flow fedFlow = fedNwIf.getFlow(getConvFlowId(networkId, flowId));
    boolean updated = false;
    // set status.
    String fedStatus = String.valueOf(fedFlow.getStatus());
    String orgStatus = String.valueOf(flow.getStatus());
    if (!(fedStatus.equals(orgStatus))) {
      updated = true;
      fedFlow.setStatus(flow.getStatus());
    }
    
    if (updated) {
      fedNwIf.putFlow(fedFlow);
    }
  }
   
  protected BasicFlow createOriginalFlowNotExistPath(String fedNwId, BasicFlow fedFlow) {
    logger.debug("");

    BasicFlow orgFlow = fedFlow.clone();

    List<BasicFlowMatch> flowMatches = fedFlow.getMatches(); /* non null */
    if (flowMatches.size() == 0) {
      logger.warn("there is no flow match");
      return null;
    }
    BasicFlowMatch flowMatch = flowMatches.get(0);

    String nodeId = flowMatch.getInNode();
    List<FlowAction> actions = fedFlow.getEdgeActions(nodeId); /* non null */
    if (actions.size() == 0) {
      logger.warn("there is no flow action");
      return null;
    }

    // convert flow's action.
    try {
      convertAction(fedNwId, orgFlow);
    } catch (Exception e) {
      logger.warn("failed convert flow's actions.");
    }

    // convert flow's match..
    try {
      convertMatch(fedNwId, orgFlow);
    } catch (Exception e) {
      logger.warn("failed convert flow's matches.");
    }

    return orgFlow;
  }

  /**
   * Check for updated other than status
   * @return  boolean true : updated other  false: updated status only
   */
  protected boolean checkUpdateFederator(
      final Flow prev,
      final Flow curr,
      final ArrayList<String> attr) {
    logger.debug("");

    BasicFlow basicFlowCurr = (BasicFlow) curr;
    BasicFlow basicFlowPrev = (BasicFlow) prev;

    if (basicFlowPrev.getEnabled() != basicFlowCurr.getEnabled()) {
      return true;
    }
    if (!basicFlowPrev.getPriority().equals(basicFlowCurr.getPriority())) {
      return true;
    }
    if (!basicFlowPrev.getOwner().equals(basicFlowCurr.getOwner())) {
      return true;
    }
    //if (!prev.getStatus().equals(curr.getStatus())) {
    //  return true;
    //}
    if (!basicFlowPrev.getMatches().equals(basicFlowCurr.matches)) {
      return true;
    }
    if (!basicFlowPrev.getPath().equals(basicFlowCurr.path)) {
      return true;
    }
    if (!basicFlowPrev.getEdgeActions().equals(basicFlowCurr.edgeActions)) {
      return true;
    }

    ArrayList<String> attributesList;
    if (attr == null) {
      attributesList = new ArrayList<String>();
    } else {
      attributesList = attr;
    }

    // make ignore list
    ArrayList<String> messageIgnoreAttributes = getIgnoreKeys(Logic.attributesFlow, attributesList);
    // attributes copy (curr -> body)
    Map<String, String> currAttributes = basicFlowCurr.getAttributes();
    for (String key : currAttributes.keySet()) {
      String oldAttr = basicFlowPrev.getAttribute(key);
      if (messageIgnoreAttributes.contains(key)
          || (oldAttr != null && oldAttr.equals(currAttributes.get(key)))) {
        continue;
      }
      return true;
    }
    logger.debug("");
    return false;
  }

  /**
   *
   * @param allkeys List of all keys.
   * @param updatekeys List of update keys.
   * @return List of the keys which aren't updated.
   */
  private ArrayList<String> getIgnoreKeys(
      final ArrayList<String> allkeys,
      final ArrayList<String> updatekeys) {

    ArrayList<String> ignorekeys = new ArrayList<String>();
    for (String key : allkeys) {
      ignorekeys.add(key);
    }

    String regex = "^" + Logic.AttrElements.ATTRIBUTES + "::.*";
    Pattern pattern = Pattern.compile(regex);
    for (String updatekey : updatekeys) {
      Matcher match = pattern.matcher(updatekey);
      if (match.find()) {
        String[] attributekey = updatekey.split("::");
        ignorekeys.remove(attributekey[1]);
      } else {
        ignorekeys.remove(updatekey);
      }
    }
    logger.debug("ignore key_list:: " + ignorekeys);
    return ignorekeys;
  }

  protected void doFlowAddedSelect(BasicFlow fedFlow) {
    logger.debug("");

    String fedNwId = getNetworkIdByType(Federator.FEDERATED_NETWORK);
    String orgNwId;

    BasicFlow orgFlow = fedFlow.clone();
    orgFlow.getPath().clear();
    orgFlow.setVersion("0");

    // convert match
    try {
      orgNwId = convertMatch(fedNwId, orgFlow);
    } catch (Exception e) {
      logger.warn("failed convert flow's actions.");
      return ;
    }

    // convert path
    for (String fedPathId : fedFlow.getPath()) {
      String orgPath = convertPath(fedNwId, fedPathId);

      if (orgPath != null) {
        orgFlow.getPath().add(orgPath);
      } else {
        Link fedLink = networkInterfaces.get(fedNwId).getLink(fedPathId);
        String srcPortId = getConvPortId(fedNwId, fedLink.getSrcNode(), fedLink.getSrcPort());
        String dstPortId = getConvPortId(fedNwId, fedLink.getDstNode(), fedLink.getDstPort());
        String[] srcPortIds = srcPortId.split(Federator.SEPARATOR);
        String[] dstPortIds = dstPortId.split(Federator.SEPARATOR);

        // convert action
        orgFlow.getEdgeActions().clear();
        setFlowAction(orgFlow, srcPortIds[1], srcPortIds[2]);
        doFlowAddedSetFlowRegister(orgNwId, orgFlow);

        // next network
        orgFlow = fedFlow.clone();
        orgFlow.getPath().clear();
        orgNwId = dstPortIds[0];
        orgFlow.setVersion("0");
        // convert match
        setFlowMatch(orgFlow, dstPortIds[1], dstPortIds[2]);
      }
    }
    // convert action
    try {
      convertAction(fedNwId, orgFlow);
    } catch (Exception e) {
      logger.warn("failed convert flow's actions.");
    }
    doFlowAddedSetFlowRegister(orgNwId, orgFlow);
  }

  protected String convertPath(String fedNwId, String fedPathId) { 
    logger.debug("");

    List<String> orgPaths = conversionTable.getLink(fedNwId, fedPathId);
    if (orgPaths.size() == 0) {
      return null;
    }
    String[] orgPath = orgPaths.get(0).split(Federator.SEPARATOR);
    return orgPath[1];
  }

  protected void doFlowAddedSetFlowRegister(
      String orgNwId,
      BasicFlow orgFlow) {
    logger.debug("");

    // Register Flow
    String fedNwId = getNetworkIdByType(Federator.FEDERATED_NETWORK);

    String fedFlowId = orgFlow.getFlowId();
    String orgFlowId = fedFlowId;
    Flow flow = networkInterfaces.get(orgNwId).getFlow(fedFlowId);
    if (flow != null) {
      Integer num = 0;
      do {
        orgFlowId = fedFlowId + "_" + num.toString();
        flow = networkInterfaces.get(orgNwId).getFlow(orgFlowId);
        num++;
      } while (flow != null);
      orgFlow.setFlowId(orgFlowId);
    }

    networkInterfaces.get(orgNwId).putFlow(orgFlow);
    // update conversionTable
    conversionTable.addEntryFlow(orgNwId, orgFlowId, fedNwId, fedFlowId);
  }

  /**
   *
   * @param flow BasicFlow.
   * @param dstNodeId Node ID.
   * @param dstPortId Port ID.
   * @return true: success. false: failed.
   */
  protected boolean setFlowMatch(
      BasicFlow flow,
      String dstNodeId,
      String dstPortId) {
    logger.debug("");

    if (flow.getMatches() == null) {
      return false;
    }
    for (BasicFlowMatch match : flow.getMatches()) {
      match.setInNode(dstNodeId);
      match.setInPort(dstPortId);
    }
    return true;
  }

  protected String convertMatch(
      String fedNwId,
      BasicFlow flow) throws Exception {
    logger.debug("");

    String networkId = null;
    for (BasicFlowMatch match : flow.getMatches()) {
      String fedNodeId = match.getInNode();
      String fedPortId = match.getInPort();
      String orgPorts = getConvPortId(
          fedNwId, fedNodeId, fedPortId);
      if (orgPorts == null) {
        continue;
      }
      String[] orgPList = orgPorts.split(Federator.SEPARATOR);
      networkId = orgPList[0];
      match.setInNode(orgPList[1]);
      match.setInPort(orgPList[2]);
    }
    return networkId;
  }

  /**
   *
   * @param flow BasicFlow.
   * @param srcNodeId Node ID.
   * @param srcPortId Port ID.
   * @return true: success. false: failed.
   */
  protected boolean setFlowAction(
      BasicFlow flow,
      String srcNodeId,
      String srcPortId) {
    logger.debug("");

    try {
      List<FlowAction> actionOutputs = new ArrayList<FlowAction>();
      flow.getEdgeActions().clear();
      actionOutputs.add(new FlowActionOutput(srcPortId));
      flow.getEdgeActions().put(srcNodeId, actionOutputs);
    } catch (NullPointerException e) {
      return false;
    }
    return true;
  }

  protected String convertAction(
      String fedNwId,
      BasicFlow flow) throws Exception {
    logger.debug("");

    String networkId = null;
    BasicFlow fedFlow = flow.clone();
    Map<String, List<FlowAction>> fedFlowActions = fedFlow.getEdgeActions();
    Map<String, List<FlowAction>> orgFlowActions = flow.getEdgeActions();

    Map<String, List<FlowAction>> targetActions =
        new HashMap<String, List<FlowAction>>();
    List<FlowAction> noActionOutputs = new ArrayList<FlowAction>();

    // Convert Action.
    for (String fedNodeId : fedFlowActions.keySet()) {
      for (FlowAction fact : fedFlowActions.get(fedNodeId)) {
        if (fact.getType().equals(
            FlowActionOutput.class.getSimpleName())) {
          FlowActionOutput output =
              (FlowActionOutput) fact;
          String fedPortId = output.getOutput();
          String orgPorts = getConvPortId(
              fedNwId, fedNodeId, fedPortId);
          if (orgPorts == null) {
            continue;
          }
          String[] orgPList = orgPorts.split(Federator.SEPARATOR);
          if (targetActions.containsKey(orgPList[1])) {
            targetActions.get(orgPList[1]).add(
                new FlowActionOutput(orgPList[2]));
          } else {
            List<FlowAction> target = new ArrayList<FlowAction>();
            target.add(new FlowActionOutput(orgPList[2]));
            targetActions.put(orgPList[1], target);
          }
          networkId = orgPList[0];
        } else {
          // no FlowActionOutput
          noActionOutputs.add(fact);
        }
      }
    }
    // Reset dstAction.
    orgFlowActions.clear();
    for (String nodeId : targetActions.keySet()) {
      // set target action
      orgFlowActions.put(nodeId, targetActions.get(nodeId));
      // set no target action
      for (FlowAction fact : noActionOutputs) {
        orgFlowActions.get(nodeId).add(fact);
      }
    }
    return networkId;
  }

  /**
   *
   * @param connType Type of the network.
   * @return ID for the network.
   */
  protected final String getNetworkIdByType(final String connType) {
    logger.debug("");

    if (connType == null) {
      return null;
    }
    ArrayList<String> convNetowrkId =
        conversionTable.getConnectionList(connType);
    if (convNetowrkId.size() == 0) {
      return null;
    }
    return convNetowrkId.get(0);
  }

  /**
   *
   * @param networkId ID for network.
   * @param nodeId ID for link in the network.
   * @return ID for node in the federated network.
   */
  protected final String getConvNodeId(
      final String networkId,
      final String nodeId) {
    logger.debug("");

    if (networkId == null || nodeId == null) {
      return null;
    }
    ArrayList<String> convNodeId =
        conversionTable.getNode(networkId, nodeId);
    if (convNodeId.size() == 0) {
      return null;
    }
    return convNodeId.get(0);
  }

  /**
   *
   * @param networkId ID for network.
   * @param nodeId ID for link in the network.
   * @param portId ID for port in the network.
   * @return ID for port in the federated network.
   */
  protected final String getConvPortId(
      final String networkId,
      final String nodeId,
      final String portId) {
    logger.debug("");

    if (networkId == null || nodeId == null || portId == null) {
      return null;
    }
    ArrayList<String> convPortId =
        conversionTable.getPort(networkId, nodeId, portId);
    if (convPortId.size() == 0) {
      return null;
    }
    return convPortId.get(0);
  }

  /**
   *
   * @param networkId ID for network.
   * @param linkId ID for link in the network.
   * @return ID link for in the federated network.
   */
  protected final String getConvLinkId(String networkId, String linkId) {
    if (networkId == null || linkId == null) {
      return null;
    }
    ArrayList<String> convLinkId =
        conversionTable.getLink(networkId, linkId);
    if (convLinkId.size() == 0) {
      return null;
    }
    return convLinkId.get(0);
  }

  /**
   *
   * @param networkId ID for network.
   * @param flowId ID for link in the network.
   * @return ID link for in the federated network.
   */
  protected final String getConvFlowId(String networkId, String flowId) {
    if (networkId == null || flowId == null) {
      return null;
    }
    ArrayList<String> convFlowId = conversionTable.getFlow(networkId, flowId);
    if (convFlowId.size() == 0) {
      return null;
    }
    String[] fedFlowIds = convFlowId.get(0).split(Federator.SEPARATOR);
    return fedFlowIds[1];
  }
}
