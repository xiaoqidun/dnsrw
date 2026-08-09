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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DnsConfig {
    public static final String PREFERENCES_GROUP = "dns_config";
    public static final String PREFERENCES_KEY = "configuration";

    public enum NetworkKind {
        WIFI,
        MOBILE
    }

    public enum Mode {
        UNCHANGED,
        CUSTOM,
        WIFI_DEFAULT,
        MOBILE_DEFAULT
    }

    public record Rule(Mode mode, List<String> customDns) {
        public Rule {
            customDns = List.copyOf(customDns);
        }
    }

    private List<String> wifiDefault = List.of();
    private List<String> mobileDefault = List.of();
    private final LinkedHashMap<String, Rule> wifiRules = new LinkedHashMap<>();
    private final LinkedHashMap<String, Rule> simRules = new LinkedHashMap<>();

    public static DnsConfig empty() {
        return new DnsConfig();
    }

    public static DnsConfig fromJson(String json) {
        DnsConfig config = new DnsConfig();
        if (json == null || json.isBlank()) {
            return config;
        }

        try {
            JSONObject root = new JSONObject(json);
            config.wifiDefault = readAddresses(root.optJSONArray("wifiDefault"));
            config.mobileDefault = readAddresses(root.optJSONArray("mobileDefault"));
            readRules(root.optJSONArray("wifiRules"), config.wifiRules);
            readRules(root.optJSONArray("simRules"), config.simRules);
            return config;
        } catch (JSONException | IllegalArgumentException ignored) {
            return new DnsConfig();
        }
    }

    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("wifiDefault", writeAddresses(wifiDefault));
            root.put("mobileDefault", writeAddresses(mobileDefault));
            root.put("wifiRules", writeRules(wifiRules));
            root.put("simRules", writeRules(simRules));
            return root.toString();
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public List<String> wifiDefault() {
        return wifiDefault;
    }

    public void setWifiDefault(List<String> addresses) {
        wifiDefault = List.copyOf(addresses);
    }

    public List<String> mobileDefault() {
        return mobileDefault;
    }

    public void setMobileDefault(List<String> addresses) {
        mobileDefault = List.copyOf(addresses);
    }

    public Map<String, Rule> wifiRules() {
        return Collections.unmodifiableMap(wifiRules);
    }

    public Map<String, Rule> simRules() {
        return Collections.unmodifiableMap(simRules);
    }

    public void putWifiRule(String ssid, Rule rule) {
        wifiRules.put(ssid, rule);
    }

    public void removeWifiRule(String ssid) {
        wifiRules.remove(ssid);
    }

    public void reorderWifiRules(List<String> ids) {
        reorderRules(wifiRules, ids);
    }

    public void putSimRule(String simId, Rule rule) {
        simRules.put(simId, rule);
    }

    public void removeSimRule(String simId) {
        simRules.remove(simId);
    }

    public void reorderSimRules(List<String> ids) {
        reorderRules(simRules, ids);
    }

    public List<String> resolve(NetworkKind kind, String identity) {
        Rule rule = identity == null
                ? null
                : (kind == NetworkKind.WIFI ? wifiRules.get(identity) : simRules.get(identity));
        if (rule == null) {
            return defaultFor(kind);
        }

        return switch (rule.mode) {
            case WIFI_DEFAULT -> wifiDefault;
            case MOBILE_DEFAULT -> mobileDefault;
            case UNCHANGED -> null;
            case CUSTOM -> rule.customDns;
        };
    }

    private List<String> defaultFor(NetworkKind kind) {
        return kind == NetworkKind.WIFI ? wifiDefault : mobileDefault;
    }

    private static List<String> readAddresses(JSONArray array) throws JSONException {
        if (array == null) {
            return List.of();
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < array.length(); index++) {
            if (index > 0) {
                text.append(' ');
            }
            text.append(array.getString(index));
        }
        return DnsAddressParser.parseText(text.toString());
    }

    private static JSONArray writeAddresses(List<String> addresses) {
        JSONArray array = new JSONArray();
        for (String address : addresses) {
            array.put(address);
        }
        return array;
    }

    private static void readRules(
            JSONArray array,
            LinkedHashMap<String, Rule> destination
    ) throws JSONException {
        if (array == null) {
            return;
        }
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            String id = item.getString("id");
            Mode mode = Mode.valueOf(item.getString("mode"));
            List<String> custom = readAddresses(item.optJSONArray("dns"));
            if (!id.isBlank() && (mode != Mode.CUSTOM || !custom.isEmpty())) {
                destination.put(id, new Rule(mode, custom));
            }
        }
    }

    private static JSONArray writeRules(Map<String, Rule> rules) throws JSONException {
        JSONArray array = new JSONArray();
        for (Map.Entry<String, Rule> entry : rules.entrySet()) {
            JSONObject item = new JSONObject();
            item.put("id", entry.getKey());
            item.put("mode", entry.getValue().mode.name());
            item.put("dns", writeAddresses(entry.getValue().customDns));
            array.put(item);
        }
        return array;
    }

    private static void reorderRules(
            LinkedHashMap<String, Rule> rules,
            List<String> ids
    ) {
        LinkedHashMap<String, Rule> ordered = new LinkedHashMap<>();
        for (String id : ids) {
            ordered.put(id, rules.get(id));
        }
        rules.clear();
        rules.putAll(ordered);
    }
}
