# NetFetch Network Architecture

## Overview

NetFetch is an Android network-sharing application.

NetFetch is designed to operate directly on the user's Android device.

NetFetch does not require a NetFetch cloud server for its local
network-sharing functionality.

## Local Gateway

The Android device acts as the network gateway.

NetFetch uses Android networking APIs to provide local network
routing and network-sharing functionality.

Network traffic is handled through the network connection available
to the Android device.

NetFetch does not require a NetFetch-owned cloud server for its
local gateway functionality.

## Hotspot and Local Networking

NetFetch can provide network access to connected devices through
Android hotspot and local networking facilities.

Local HTTP and SOCKS networking services may be exposed for
connected clients.

These services operate between the Android device and connected
clients on the local network.

## VPN

NetFetch can use Android's VPN APIs for network routing functionality.

The VPN interface is created on the user's Android device.

NetFetch does not require a NetFetch cloud VPN server for this
local VPN functionality.

## Privacy

NetFetch does not include advertising SDKs.

NetFetch does not require a NetFetch account.

NetFetch does not require a NetFetch cloud backend for its local
gateway functionality.

Network traffic can still pass through the Internet service or
network provider selected by the user.

## Open Source

NetFetch source code is publicly available in the project repository.

NetFetch is released under the Apache License 2.0.
