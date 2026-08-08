/*
 * Copyright 2026 肖其顿 (XIAO QI DUN)
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

package me.aite.dnsrw;

import android.net.InetAddresses;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class DnsAddressParser {
    private DnsAddressParser() {
    }

    public static List<String> parseText(String text) {
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        for (String token : text.trim().split("[,;\\s]+")) {
            if (!InetAddresses.isNumericAddress(token)) {
                throw new IllegalArgumentException("Invalid DNS address: " + token);
            }
            addresses.add(InetAddresses.parseNumericAddress(token).getHostAddress());
        }
        return List.copyOf(addresses);
    }

    public static List<InetAddress> toInetAddresses(List<String> addresses) {
        ArrayList<InetAddress> result = new ArrayList<>(addresses.size());
        for (String address : addresses) {
            result.add(InetAddresses.parseNumericAddress(address));
        }
        return result;
    }

    public static String format(List<String> addresses) {
        return String.join("\n", addresses);
    }
}
