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

import android.annotation.SuppressLint;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;

public final class DnsRwModule extends XposedModule {
    private static final String SYSTEM_SERVICE_MANAGER =
            "com.android.server.SystemServiceManager";
    private static final String CONNECTIVITY_INITIALIZER =
            "com.android.server.ConnectivityServiceInitializer";
    private static final String CONNECTIVITY_SERVICE =
            "android.net.connectivity.com.android.server.ConnectivityService";
    private static final String NETWORK_AGENT_INFO =
            "android.net.connectivity.com.android.server.connectivity.NetworkAgentInfo";
    private static final String SERVICE_START_HOOK_ID = "locate-connectivity-classloader";
    private static final String DNS_HOOK_ID = "rewrite-network-dns";
    private static final String MODULE_PACKAGE = "me.aite.dnsrw";
    private static final String UNKNOWN_SSID = "<unknown ssid>";

    private volatile DnsConfig configuration = DnsConfig.empty();
    private SharedPreferences remotePreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private SubscriptionManager.OnSubscriptionsChangedListener subscriptionListener;
    private final WeakHashMap<Object, List<InetAddress>> originalDns = new WeakHashMap<>();
    private Field networkAgentInfosField;
    private Field linkPropertiesField;
    private Constructor<LinkProperties> linkPropertiesCopyConstructor;
    private Method handleUpdateLinkProperties;
    private Method getWifiIpAssignment;
    private volatile Object connectivityService;
    private volatile Handler connectivityHandler;

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        try {
            connectPreferences();
            installConnectivityClassLoaderHook(param.getClassLoader());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void connectPreferences() {
        remotePreferences = getRemotePreferences(DnsConfig.PREFERENCES_GROUP);
        reloadConfiguration();
        preferenceListener = (preferences, key) -> {
            if (DnsConfig.PREFERENCES_KEY.equals(key)) {
                reloadConfiguration();
                reapplyConfiguration();
            }
        };
        remotePreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    private void reloadConfiguration() {
        configuration = DnsConfig.fromJson(
                remotePreferences.getString(DnsConfig.PREFERENCES_KEY, "")
        );
    }

    @SuppressLint("PrivateApi")
    private void installConnectivityClassLoaderHook(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> managerClass = Class.forName(SYSTEM_SERVICE_MANAGER, false, classLoader);
        Field systemContextField = accessibleField(managerClass, "mContext");
        Method startService = managerClass.getDeclaredMethod("startService", Class.class);
        startService.setAccessible(true);

        hook(startService)
                .setId(SERVICE_START_HOOK_ID)
                .setPriority(PRIORITY_HIGHEST)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Class<?> serviceClass = (Class<?>) chain.getArg(0);
                    String serviceName = serviceClass.getName();
                    if (CONNECTIVITY_INITIALIZER.equals(serviceName)) {
                        try {
                            installConnectivityHook(serviceClass.getClassLoader());
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                    Object result = chain.proceed();
                    if (CONNECTIVITY_INITIALIZER.equals(serviceName)) {
                        Context context = (Context) systemContextField.get(chain.getThisObject());
                        startNetworkObservations(context);
                    }
                    return result;
                });
    }

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    private void installConnectivityHook(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> serviceClass = Class.forName(CONNECTIVITY_SERVICE, false, classLoader);
        Class<?> networkAgentInfoClass = Class.forName(
                NETWORK_AGENT_INFO,
                false,
                classLoader
        );
        Field contextField = accessibleField(serviceClass, "mContext");
        Field handlerField = accessibleField(serviceClass, "mHandler");
        networkAgentInfosField = accessibleField(serviceClass, "mNetworkAgentInfos");
        Field capabilitiesField = accessibleField(
                networkAgentInfoClass,
                "networkCapabilities"
        );
        linkPropertiesField = accessibleField(networkAgentInfoClass, "linkProperties");
        getWifiIpAssignment = WifiConfiguration.class.getDeclaredMethod("getIpAssignment");
        getWifiIpAssignment.setAccessible(true);
        linkPropertiesCopyConstructor = LinkProperties.class.getDeclaredConstructor(
                LinkProperties.class
        );
        linkPropertiesCopyConstructor.setAccessible(true);
        handleUpdateLinkProperties = serviceClass.getDeclaredMethod(
                "handleUpdateLinkProperties",
                networkAgentInfoClass,
                LinkProperties.class
        );
        handleUpdateLinkProperties.setAccessible(true);

        hook(handleUpdateLinkProperties)
                .setId(DNS_HOOK_ID)
                .setPriority(PRIORITY_HIGHEST)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object networkAgentInfo = chain.getArg(0);
                    LinkProperties linkProperties = (LinkProperties) chain.getArg(1);
                    connectivityService = chain.getThisObject();
                    connectivityHandler = (Handler) handlerField.get(connectivityService);
                    List<InetAddress> incomingDns = List.copyOf(
                            linkProperties.getDnsServers()
                    );
                    originalDns.put(networkAgentInfo, incomingDns);
                    NetworkCapabilities capabilities = (NetworkCapabilities)
                            capabilitiesField.get(networkAgentInfo);
                    Context context = (Context) contextField.get(chain.getThisObject());
                    List<String> addresses = resolveNetwork(context, capabilities);
                    if (addresses != null && !addresses.isEmpty()) {
                        List<InetAddress> dns = DnsAddressParser.toInetAddresses(addresses);
                        linkProperties.setDnsServers(dns);
                    }
                    return chain.proceed();
                });
    }

    private void reapplyConfiguration() {
        Object service = connectivityService;
        Handler handler = connectivityHandler;
        if (service == null || handler == null) {
            return;
        }
        handler.post(() -> {
            try {
                Iterable<?> networks = (Iterable<?>) networkAgentInfosField.get(service);
                for (Object network : networks) {
                    LinkProperties current = (LinkProperties) linkPropertiesField.get(network);
                    LinkProperties update = linkPropertiesCopyConstructor.newInstance(current);
                    List<InetAddress> dns = originalDns.get(network);
                    if (dns != null) {
                        update.setDnsServers(dns);
                    }
                    handleUpdateLinkProperties.invoke(service, network, update);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    private List<String> resolveNetwork(
            Context context,
            NetworkCapabilities capabilities
    ) throws ReflectiveOperationException {
        if (capabilities == null
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return null;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiConnection wifi = getWifiConnection(capabilities);
            if (wifi == null || isStaticWifi(context, wifi.networkId)) {
                return null;
            }
            return configuration.resolve(DnsConfig.NetworkKind.WIFI, wifi.ssid);
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            MobileIdentities mobile = getMobileIdentities(
                    context,
                    capabilities.getSubscriptionIds()
            );
            String identity = mobile.current == null ? null : mobile.current.id;
            return configuration.resolve(DnsConfig.NetworkKind.MOBILE, identity);
        } else {
            return null;
        }
    }

    private static WifiConnection getWifiConnection(NetworkCapabilities capabilities) {
        TransportInfo transportInfo = capabilities.getTransportInfo();
        if (!(transportInfo instanceof WifiInfo wifiInfo)) {
            return null;
        }
        return new WifiConnection(normalizeSsid(wifiInfo.getSSID()), wifiInfo.getNetworkId());
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private boolean isStaticWifi(Context context, int currentNetworkId)
            throws ReflectiveOperationException {
        WifiManager manager = context.getSystemService(WifiManager.class);
        for (WifiConfiguration configuration : manager.getConfiguredNetworks()) {
            if (configuration.networkId == currentNetworkId
                    && "STATIC".equals(String.valueOf(
                    getWifiIpAssignment.invoke(configuration)
            ))) {
                return true;
            }
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private static void sendSavedWifiObservations(Context context) {
        WifiManager manager = context.getSystemService(WifiManager.class);
        ArrayList<String> ids = new ArrayList<>();
        for (WifiConfiguration configuration : manager.getConfiguredNetworks()) {
            String ssid = normalizeSsid(configuration.SSID);
            if (ssid != null) {
                ids.add(ssid);
            }
        }
        sendObservations(context, ObservationStore.TYPE_WIFI, ids, ids);
    }

    @SuppressLint("MissingPermission")
    private void startNetworkObservations(Context context) {
        context.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context receiverContext, Intent intent) {
                        sendSavedWifiObservations(receiverContext);
                        sendMobileObservations(receiverContext);
                    }
                },
                new IntentFilter(NetworkObservationReceiver.REQUEST_ACTION),
                Context.RECEIVER_EXPORTED
        );

        SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
        subscriptionListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
            @Override
            public void onSubscriptionsChanged() {
                sendMobileObservations(context);
            }
        };
        manager.addOnSubscriptionsChangedListener(
                context.getMainExecutor(),
                subscriptionListener
        );
    }

    private static void sendMobileObservations(Context context) {
        List<SimIdentity> active = getMobileIdentities(context, Set.of()).active;
        ArrayList<String> ids = new ArrayList<>(active.size());
        ArrayList<String> labels = new ArrayList<>(active.size());
        for (SimIdentity sim : active) {
            ids.add(sim.id);
            labels.add(sim.label);
        }
        sendObservations(context, ObservationStore.TYPE_SIM, ids, labels);
    }

    private static String normalizeSsid(String ssid) {
        if (ssid == null || UNKNOWN_SSID.equals(ssid)) {
            return null;
        }
        if (ssid.length() >= 2 && ssid.charAt(0) == '"'
                && ssid.charAt(ssid.length() - 1) == '"') {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    @SuppressLint("MissingPermission")
    private static MobileIdentities getMobileIdentities(
            Context context,
            Set<Integer> subscriptionIds
    ) {
        SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> subscriptions = manager.getActiveSubscriptionInfoList();
        if (subscriptions == null) {
            return new MobileIdentities(null, List.of());
        }

        int activeDataId = SubscriptionManager.getActiveDataSubscriptionId();
        ArrayList<SimIdentity> active = new ArrayList<>(subscriptions.size());
        SimIdentity current = null;
        SimIdentity activeData = null;
        for (SubscriptionInfo info : subscriptions) {
            SimIdentity identity = getSimIdentity(info);
            if (identity == null) {
                continue;
            }
            active.add(identity);
            int subscriptionId = info.getSubscriptionId();
            if (subscriptionIds.contains(subscriptionId)) {
                current = identity;
            }
            if (subscriptionId == activeDataId) {
                activeData = identity;
            }
        }
        return new MobileIdentities(current != null ? current : activeData, active);
    }

    private static SimIdentity getSimIdentity(SubscriptionInfo info) {
        String iccId = normalizeIccid(info.getIccId());
        if (iccId == null || iccId.isBlank()) {
            return null;
        }
        String name = String.valueOf(info.getDisplayName());
        if (name.isBlank()) {
            name = String.valueOf(info.getCarrierName());
        }
        int slotIndex = info.getSimSlotIndex();
        String location = slotIndex >= 0
                ? "卡槽 " + (slotIndex + 1)
                : "移动网络";
        return new SimIdentity(
                iccId,
                location + " · " + name
        );
    }

    private static String normalizeIccid(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == 'F' || value.charAt(end - 1) == 'f')) {
            end--;
        }
        return value.substring(0, end);
    }

    private static void sendObservations(
            Context context,
            String type,
            List<String> ids,
            List<String> labels
    ) {
        Intent intent = new Intent(NetworkObservationReceiver.ACTION)
                .setPackage(MODULE_PACKAGE)
                .putExtra(NetworkObservationReceiver.EXTRA_TYPE, type)
                .putExtra(NetworkObservationReceiver.EXTRA_IDS, ids.toArray(String[]::new))
                .putExtra(NetworkObservationReceiver.EXTRA_LABELS, labels.toArray(String[]::new));
        BroadcastOptions options = BroadcastOptions.makeBasic()
                .setShareIdentityEnabled(true);
        context.sendBroadcast(intent, null, options.toBundle());
    }

    private static Field accessibleField(Class<?> owner, String name)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record SimIdentity(String id, String label) {
    }

    private record MobileIdentities(SimIdentity current, List<SimIdentity> active) {
    }

    private record WifiConnection(String ssid, int networkId) {
    }

}
