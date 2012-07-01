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

/*
 * Copyright (c) 2010 Animesh Kumar  (https://github.com/anismiles)
 * Copyright (c) 2010 Strumsoft  (https://strumsoft.com)
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package org.apache.cordova;

import android.webkit.WebView;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class CordovaWebSocketClient extends org.java_websocket.client.WebSocketClient {

    private static String BLANK_MESSAGE = "";
    private static String EVENT_ON_OPEN = "onopen";
    private static String EVENT_ON_MESSAGE = "onmessage";
    private static String EVENT_ON_CLOSE = "onclose";
    private static String EVENT_ON_ERROR = "onerror";

    private WebView appView;
    private String id;

    public String getId() {
        return id;
    }

    public CordovaWebSocketClient(WebView appView, URI serverUri, Draft draft, String id) {
        super(serverUri, draft);
        this.appView = appView;
        this.id = id;
    }

    private CordovaWebSocketClient(URI serverUri, Draft draft) {
        super(serverUri, draft);
    }

    private CordovaWebSocketClient(URI serverURI) {
        super(serverURI);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        appView.post(new Runnable() {
            @Override
            public void run() {
                appView.loadUrl(buildJavaScriptData(EVENT_ON_OPEN, BLANK_MESSAGE));
            }
        });
    }

    @Override
    public void onMessage(final String message) {
        appView.post(new Runnable() {
            @Override
            public void run() {
                String sanitizedMessage = message.replace("\n", "\\n").replace("\0", "\\0");
                appView.loadUrl(buildJavaScriptData(EVENT_ON_MESSAGE, sanitizedMessage));
            }
        });
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        appView.post(new Runnable() {
            @Override
            public void run() {
                appView.loadUrl(buildJavaScriptData(EVENT_ON_CLOSE, BLANK_MESSAGE));
            }
        });
    }

    @Override
    public void onError(final Exception ex) {
        appView.post(new Runnable() {
            @Override
            public void run() {
                appView.loadUrl(buildJavaScriptData(EVENT_ON_ERROR, ex.getMessage()));
            }
        });
    }

    /**
     * Builds text for javascript engine to invoke proper event method with
     * proper data.
     *
     * @param event websocket event (onOpen, onMessage etc.)
     * @param msg   Text message received from websocket server
     * @return
     */
    private String buildJavaScriptData(String event, String msg) {
        String _d = "javascript:WebSocket." + event + "(" + "{" + "\"_target\":\"" + id + "\"," + "\"data\":'" + msg
                + "'" + "}" + ")";
        return _d;
    }
}