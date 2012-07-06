/*
 * Copyright 2012 Claude Mamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.cordova;

import org.apache.cordova.api.Plugin;
import org.apache.cordova.api.PluginResult;
import org.java_websocket.drafts.Draft_17;
import org.json.JSONArray;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WebSocket extends Plugin {

    private Map<String, CordovaWebSocketClient> sockets;
    private final static String ACTION_CREATE = "create";
    private final static String ACTION_CONNECT = "connect";
    private final static String ACTION_SEND = "send";
    private final static String ACTION_CLOSE = "close";
    private final static String ACTION_GET_READY_STATE = "getReadyState";

    public WebSocket() {
        super();
        sockets = new HashMap<String, CordovaWebSocketClient>();
    }

    @Override
    public boolean isSynch(String action) {
        if (action.equals(ACTION_GET_READY_STATE) || action.equals(ACTION_CREATE)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public PluginResult execute(String action, JSONArray data, String callbackId) {
        try {
            if (action.equals(ACTION_CREATE)) {
                CordovaWebSocketClient webSocketClient = new CordovaWebSocketClient(webView, new URI(data.getString(0)), new Draft_17(), getRandonUniqueId());
                sockets.put(webSocketClient.getId(), webSocketClient);
                return new PluginResult(PluginResult.Status.OK, webSocketClient.getId());
            } else if (action.equals(ACTION_CONNECT)) {
                sockets.get(data.get(0)).connect();
                return new PluginResult(PluginResult.Status.OK);
            } else if (action.equals(ACTION_SEND)) {
                sockets.get(data.get(0)).send(data.get(1).toString());
                return new PluginResult(PluginResult.Status.OK);
            } else if (action.equals(ACTION_CLOSE)) {
                sockets.get(data.get(0)).close();
                return new PluginResult(PluginResult.Status.OK);
            } else if (action.equals(ACTION_GET_READY_STATE)) {
                return new PluginResult(PluginResult.Status.OK, sockets.get(data.get(0)).getReadyState());
            }

            return new PluginResult(PluginResult.Status.INVALID_ACTION);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getRandonUniqueId() {
        return "WEBSOCKET." + new Random().nextInt(100);
    }

}
