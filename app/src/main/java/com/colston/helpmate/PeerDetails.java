/*
 * Copyright 2023 Colston Bod-oy
 *
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.colston.helpmate;

public class PeerDetails {
  static PeerDetails peerDetails;
  String peerAddress;

  public static PeerDetails getInstance() {
    if (peerDetails == null) {
      peerDetails = new PeerDetails();
    }
    return peerDetails;
  }

  public String getPeerAddress() {
    return peerAddress;
  }

  public void setPeerAddress(String peerAddress) {
    this.peerAddress = peerAddress;
  }
}
