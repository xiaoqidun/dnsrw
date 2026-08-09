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

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.libxposed.service.XposedService;

public final class MainActivity extends AppCompatActivity {
    private static final String APPEARANCE_PREFERENCES = "appearance";
    private static final String THEME_COLOR_KEY = "theme_color";
    private static final int THEME_COLOR_PURPLE = 1;
    private static final int[] THEME_COLOR_SEEDS = {
            0,
            0xFF7E57C2,
            0xFF3F51B5,
            0xFF006C4C,
            0xFFA04400
    };
    private static final Typeface MONOSPACE = Typeface.create(
            "sans-serif-monospace",
            Typeface.NORMAL
    );

    private final DnsRwApplication.ServiceListener serviceListener =
            this::onServiceChanged;
    private final SharedPreferences.OnSharedPreferenceChangeListener remoteListener =
            (preferences, key) -> {
                if (DnsConfig.PREFERENCES_KEY.equals(key)) {
                    runOnUiThread(this::loadConfiguration);
                }
            };
    private final SharedPreferences.OnSharedPreferenceChangeListener observationListener =
            (preferences, key) -> runOnUiThread(this::renderRules);

    private TextView serviceStatus;
    private ImageView serviceStatusIcon;
    private MaterialCardView serviceStatusCard;
    private EditText wifiDefaultDns;
    private EditText wifiDefaultDnsSecondary;
    private EditText mobileDefaultDns;
    private EditText mobileDefaultDnsSecondary;
    private MaterialButton saveDefaults;
    private MaterialButton themeButton;
    private MaterialButton aboutButton;
    private MaterialButton addWifiRule;
    private MaterialButton addSimRule;
    private RecyclerView wifiRules;
    private RecyclerView simRules;
    private RuleAdapter wifiRuleAdapter;
    private RuleAdapter simRuleAdapter;
    private ItemTouchHelper wifiRuleTouchHelper;
    private ItemTouchHelper simRuleTouchHelper;

    private SharedPreferences remotePreferences;
    private DnsConfig configuration = DnsConfig.empty();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyThemeColor();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupSystemBarInsets(findViewById(R.id.screen));

        serviceStatus = findViewById(R.id.service_status);
        serviceStatusIcon = findViewById(R.id.service_status_icon);
        serviceStatusCard = findViewById(R.id.service_status_card);
        wifiDefaultDns = findViewById(R.id.wifi_default_dns);
        wifiDefaultDnsSecondary = findViewById(R.id.wifi_default_dns_secondary);
        mobileDefaultDns = findViewById(R.id.mobile_default_dns);
        mobileDefaultDnsSecondary = findViewById(R.id.mobile_default_dns_secondary);
        saveDefaults = findViewById(R.id.save_defaults);
        themeButton = findViewById(R.id.theme_button);
        aboutButton = findViewById(R.id.about_button);
        addWifiRule = findViewById(R.id.add_wifi_rule);
        addSimRule = findViewById(R.id.add_sim_rule);
        wifiRules = findViewById(R.id.wifi_rules);
        simRules = findViewById(R.id.sim_rules);

        wifiRuleAdapter = new RuleAdapter(
                true,
                item -> showWifiRuleDialog(item.id()),
                item -> confirmDelete(item.id(), item.label(), true),
                holder -> wifiRuleTouchHelper.startDrag(holder)
        );
        simRuleAdapter = new RuleAdapter(
                false,
                item -> showRuleDialog(false, item.id(), item.label()),
                item -> confirmDelete(item.id(), item.label(), false),
                holder -> simRuleTouchHelper.startDrag(holder)
        );
        wifiRuleTouchHelper = attachRuleTouchHelper(wifiRules, wifiRuleAdapter, true);
        simRuleTouchHelper = attachRuleTouchHelper(simRules, simRuleAdapter, false);

        saveDefaults.setOnClickListener(view -> {
            if (captureDefaults()) {
                saveConfiguration();
            }
        });
        themeButton.setOnClickListener(view -> showThemeColorDialog());
        aboutButton.setOnClickListener(view -> showAboutDialog());
        addWifiRule.setOnClickListener(view -> showWifiRuleDialog(null));
        addSimRule.setOnClickListener(view -> showSimPicker());
        renderRules();
    }

    private void applyThemeColor() {
        int selected = getSharedPreferences(
                APPEARANCE_PREFERENCES,
                MODE_PRIVATE
        ).getInt(THEME_COLOR_KEY, THEME_COLOR_PURPLE);
        if (selected == 0) {
            DynamicColors.applyToActivityIfAvailable(this);
            return;
        }
        DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                .setContentBasedSource(THEME_COLOR_SEEDS[selected])
                .build();
        DynamicColors.applyToActivityIfAvailable(this, options);
    }

    private void showThemeColorDialog() {
        SharedPreferences preferences = getSharedPreferences(
                APPEARANCE_PREFERENCES,
                MODE_PRIVATE
        );
        int selected = preferences.getInt(THEME_COLOR_KEY, THEME_COLOR_PURPLE);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_color)
                .setSingleChoiceItems(R.array.theme_colors, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which != selected) {
                        preferences.edit().putInt(THEME_COLOR_KEY, which).apply();
                        recreate();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAboutDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_about, null, false);
        content.findViewById(R.id.personal_homepage).setOnClickListener(
                view -> openWebPage(R.string.personal_homepage_url)
        );
        content.findViewById(R.id.repository).setOnClickListener(
                view -> openWebPage(R.string.repository_url)
        );
        new MaterialAlertDialogBuilder(this)
                .setView(content)
                .show();
    }

    private void openWebPage(int url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(url))));
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObservationStore.preferences(this).registerOnSharedPreferenceChangeListener(
                observationListener
        );
        DnsRwApplication.addServiceListener(serviceListener);
    }

    @Override
    protected void onStop() {
        ObservationStore.preferences(this).unregisterOnSharedPreferenceChangeListener(
                observationListener
        );
        DnsRwApplication.removeServiceListener(serviceListener);
        super.onStop();
    }

    private void onServiceChanged(XposedService service) {
        if (remotePreferences != null) {
            remotePreferences.unregisterOnSharedPreferenceChangeListener(remoteListener);
            remotePreferences = null;
        }

        if (service == null) {
            setServiceStatus(
                    R.string.service_disconnected,
                    com.google.android.material.R.attr.colorOnErrorContainer,
                    com.google.android.material.R.attr.colorErrorContainer
            );
            setEditingEnabled(false);
            return;
        }

        remotePreferences = service.getRemotePreferences(DnsConfig.PREFERENCES_GROUP);
        remotePreferences.registerOnSharedPreferenceChangeListener(remoteListener);
        boolean systemScopeEnabled = service.getScope().contains("system");
        setServiceStatus(
                systemScopeEnabled ? R.string.service_ready : R.string.scope_missing,
                systemScopeEnabled
                        ? com.google.android.material.R.attr.colorOnPrimaryContainer
                        : com.google.android.material.R.attr.colorOnTertiaryContainer,
                systemScopeEnabled
                        ? com.google.android.material.R.attr.colorPrimaryContainer
                        : com.google.android.material.R.attr.colorTertiaryContainer
        );
        setEditingEnabled(true);
        loadConfiguration();
        if (systemScopeEnabled) {
            sendBroadcast(new Intent(NetworkObservationReceiver.REQUEST_ACTION));
        }
    }

    private void setServiceStatus(
            int text,
            int foregroundAttribute,
            int backgroundAttribute
    ) {
        int color = MaterialColors.getColor(serviceStatus, foregroundAttribute);
        serviceStatus.setText(text);
        serviceStatus.setTextColor(color);
        android.content.res.ColorStateList colors =
                android.content.res.ColorStateList.valueOf(color);
        serviceStatusIcon.setImageTintList(colors);
        themeButton.setIconTint(colors);
        aboutButton.setIconTint(colors);
        serviceStatusCard.setCardBackgroundColor(
                MaterialColors.getColor(serviceStatusCard, backgroundAttribute)
        );
    }

    private static void setupSystemBarInsets(View screen) {
        int left = screen.getPaddingLeft();
        int top = screen.getPaddingTop();
        int right = screen.getPaddingRight();
        int bottom = screen.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(screen, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    left + insets.left,
                    top + insets.top,
                    right + insets.right,
                    bottom + insets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(screen);
    }

    private void setEditingEnabled(boolean enabled) {
        wifiDefaultDns.setEnabled(enabled);
        wifiDefaultDnsSecondary.setEnabled(enabled);
        mobileDefaultDns.setEnabled(enabled);
        mobileDefaultDnsSecondary.setEnabled(enabled);
        saveDefaults.setEnabled(enabled);
        addWifiRule.setEnabled(enabled);
        addSimRule.setEnabled(enabled);
    }

    private void loadConfiguration() {
        if (remotePreferences == null) {
            return;
        }
        configuration = DnsConfig.fromJson(
                remotePreferences.getString(DnsConfig.PREFERENCES_KEY, "")
        );
        setDnsPair(configuration.wifiDefault(), wifiDefaultDns, wifiDefaultDnsSecondary);
        setDnsPair(configuration.mobileDefault(), mobileDefaultDns, mobileDefaultDnsSecondary);
        renderRules();
    }

    private boolean captureDefaults() {
        List<String> wireless = readDnsPair(wifiDefaultDns, wifiDefaultDnsSecondary);
        List<String> mobile = readDnsPair(mobileDefaultDns, mobileDefaultDnsSecondary);
        if (wireless == null || mobile == null) {
            return false;
        }
        configuration.setWifiDefault(wireless);
        configuration.setMobileDefault(mobile);
        return true;
    }

    private boolean saveConfiguration() {
        return remotePreferences != null
                && remotePreferences.edit()
                .putString(DnsConfig.PREFERENCES_KEY, configuration.toJson())
                .commit();
    }

    private void renderRules() {
        renderRuleGroup(wifiRules, wifiRuleAdapter, configuration.wifiRules(), true);
        renderRuleGroup(simRules, simRuleAdapter, configuration.simRules(), false);
    }

    private void renderRuleGroup(
            RecyclerView container,
            RuleAdapter adapter,
            Map<String, DnsConfig.Rule> rules,
            boolean wifi
    ) {
        container.setVisibility(rules.isEmpty() ? View.GONE : View.VISIBLE);
        Map<String, String> observations = wifi
                ? Map.of()
                : ObservationStore.load(this, ObservationStore.TYPE_SIM);
        ArrayList<RuleAdapter.Item> items = new ArrayList<>(rules.size());
        for (Map.Entry<String, DnsConfig.Rule> entry : rules.entrySet()) {
            String id = entry.getKey();
            String observedLabel = wifi ? id : observations.get(id);
            String label = observedLabel;
            if (label == null) {
                label = getString(R.string.sim_unavailable);
            }
            items.add(new RuleAdapter.Item(
                    id,
                    label,
                    entry.getValue()
            ));
        }
        adapter.setItems(items);
    }

    private ItemTouchHelper attachRuleTouchHelper(
            RecyclerView list,
            RuleAdapter adapter,
            boolean wifi
    ) {
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                0
        ) {
            @Override
            public boolean onMove(
                    RecyclerView recyclerView,
                    RecyclerView.ViewHolder source,
                    RecyclerView.ViewHolder target
            ) {
                return adapter.move(
                        source.getBindingAdapterPosition(),
                        target.getBindingAdapterPosition()
                );
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void clearView(
                    RecyclerView recyclerView,
                    RecyclerView.ViewHolder viewHolder
            ) {
                super.clearView(recyclerView, viewHolder);
                if (adapter.consumeOrderChanged()) {
                    saveRuleOrder(adapter, wifi);
                }
            }
        });
        helper.attachToRecyclerView(list);
        return helper;
    }

    private void saveRuleOrder(RuleAdapter adapter, boolean wifi) {
        if (!captureDefaults()) {
            renderRules();
            return;
        }
        if (wifi) {
            configuration.reorderWifiRules(adapter.orderedIds());
        } else {
            configuration.reorderSimRules(adapter.orderedIds());
        }
        saveConfiguration();
    }

    private void showWifiRuleDialog(String originalId) {
        if (originalId != null) {
            showRuleDialog(true, originalId, originalId);
            return;
        }

        Map<String, String> seenWifi = ObservationStore.load(this, ObservationStore.TYPE_WIFI);
        if (seenWifi.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.select_wireless_network)
                    .setMessage(R.string.no_seen_wireless)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(
                            R.string.manual_wireless_network,
                            (dialog, which) -> showManualWirelessRuleDialog()
                    )
                    .show();
            return;
        }

        List<String> ids = new ArrayList<>(seenWifi.keySet());
        String[] labels = new String[ids.size() + 1];
        for (int index = 0; index < ids.size(); index++) {
            labels[index] = ids.get(index);
        }
        labels[ids.size()] = getString(R.string.manual_wireless_network);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_wireless_network)
                .setAdapter(createAdapter(
                        android.R.layout.simple_list_item_1,
                        labels
                ), (dialog, which) -> {
                    if (which == ids.size()) {
                        showManualWirelessRuleDialog();
                    } else {
                        String id = ids.get(which);
                        showRuleDialog(true, id, id);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showManualWirelessRuleDialog() {
        TextInputEditText identity = new TextInputEditText(this);
        configureTextInput(identity);
        showRuleDialog(true, null, null, identity);
    }

    private void showSimPicker() {
        Map<String, String> seenSims = ObservationStore.load(this, ObservationStore.TYPE_SIM);
        if (seenSims.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.select_sim)
                    .setMessage(R.string.no_seen_sim)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        List<String> ids = new ArrayList<>(seenSims.keySet());
        String[] labels = new String[ids.size()];
        for (int index = 0; index < ids.size(); index++) {
            labels[index] = seenSims.get(ids.get(index));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_sim)
                .setAdapter(createMobileNetworkAdapter(labels), (dialog, which) -> showRuleDialog(
                        false,
                        ids.get(which),
                        seenSims.get(ids.get(which))
                ))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRuleDialog(boolean wifi, String id, String label) {
        showRuleDialog(wifi, id, label, null);
    }

    private void showRuleDialog(
            boolean wifi,
            String originalId,
            String label,
            TextInputEditText identityInput
    ) {
        DnsConfig.Rule existing = originalId == null
                ? null
                : (wifi
                ? configuration.wifiRules().get(originalId)
                : configuration.simRules().get(originalId));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(4));

        if (identityInput != null) {
            TextInputLayout identityField = createOutlinedField(R.string.ssid_label);
            identityField.addView(identityInput);
            addField(content, identityField, 0);
        }

        int[] selectedMode = {
                existing == null
                        ? DnsConfig.Mode.UNCHANGED.ordinal()
                        : existing.mode().ordinal()
        };
        String[] modeLabels = getResources().getStringArray(R.array.rule_modes);
        TextInputLayout modeField = createOutlinedField(R.string.rule_mode);
        modeField.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        MaterialAutoCompleteTextView mode = new MaterialAutoCompleteTextView(this);
        mode.setInputType(InputType.TYPE_NULL);
        mode.setAdapter(createAdapter(
                android.R.layout.simple_dropdown_item_1line,
                modeLabels
        ));
        mode.setText(modeLabels[selectedMode[0]], false);
        modeField.addView(mode);
        addField(content, modeField, identityInput == null ? 0 : 12);

        LinearLayout customDns = new LinearLayout(this);
        customDns.setOrientation(LinearLayout.VERTICAL);
        TextInputEditText customDnsPrimary = createDnsInput();
        TextInputLayout primaryField = createOutlinedField(R.string.primary_dns);
        primaryField.addView(customDnsPrimary);
        addField(customDns, primaryField, 12);
        TextInputEditText customDnsSecondary = createDnsInput();
        TextInputLayout secondaryField = createOutlinedField(R.string.secondary_dns);
        secondaryField.addView(customDnsSecondary);
        addField(customDns, secondaryField, 8);
        content.addView(customDns);

        if (existing != null) {
            setDnsPair(existing.customDns(), customDnsPrimary, customDnsSecondary);
        }

        Runnable updateCustomState = () -> customDns.setVisibility(
                selectedMode[0] == DnsConfig.Mode.CUSTOM.ordinal()
                        ? View.VISIBLE
                        : View.GONE
        );
        mode.setOnItemClickListener((parent, view, position, id) -> {
            selectedMode[0] = position;
            updateCustomState.run();
        });
        updateCustomState.run();

        String title = label == null
                ? getString(R.string.wifi_rules_title)
                : label;
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(wifi ? title : RuleAdapter.formatMobileLabel(title))
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String id = originalId;
                    if (identityInput != null) {
                        id = identityInput.getText().toString();
                        if (id.isBlank()) {
                            identityInput.setError(getString(R.string.empty_ssid));
                            return;
                        }
                    }

                    DnsConfig.Mode selected = DnsConfig.Mode.values()[selectedMode[0]];
                    List<String> addresses = List.of();
                    if (selected == DnsConfig.Mode.CUSTOM) {
                        addresses = readDnsPair(customDnsPrimary, customDnsSecondary);
                        if (addresses == null) {
                            return;
                        }
                        if (addresses.isEmpty()) {
                            customDnsPrimary.setError(getString(R.string.custom_dns_required));
                            return;
                        }
                    }

                    if (!captureDefaults()) {
                        return;
                    }
                    DnsConfig.Rule rule = new DnsConfig.Rule(selected, addresses);
                    if (wifi) {
                        configuration.putWifiRule(id, rule);
                    } else {
                        configuration.putSimRule(id, rule);
                    }
                    if (saveConfiguration()) {
                        dialog.dismiss();
                    }
                }));
        dialog.show();
    }

    private TextInputLayout createOutlinedField(int hint) {
        TextInputLayout field = new TextInputLayout(this);
        field.setHint(hint);
        field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        field.setBoxCornerRadii(dp(8), dp(8), dp(8), dp(8));
        return field;
    }

    private void addField(LinearLayout parent, View field, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMargin);
        parent.addView(field, params);
    }

    private ArrayAdapter<String> createAdapter(int layout, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, layout, values);
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        return adapter;
    }

    private ArrayAdapter<String> createMobileNetworkAdapter(String[] values) {
        return new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(RuleAdapter.formatMobileLabel(values[position]));
                return view;
            }
        };
    }

    private void confirmDelete(String id, String label, boolean wifi) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(wifi
                        ? getString(R.string.delete_message, label)
                        : RuleAdapter.formatMobileLabel(getString(R.string.delete_message, label)))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (!captureDefaults()) {
                        return;
                    }
                    if (wifi) {
                        configuration.removeWifiRule(id);
                    } else {
                        configuration.removeSimRule(id);
                    }
                    saveConfiguration();
                })
                .show();
    }

    private TextInputEditText createDnsInput() {
        TextInputEditText input = new TextInputEditText(this);
        configureTextInput(input);
        input.setTypeface(MONOSPACE);
        return input;
    }

    private void configureTextInput(EditText input) {
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    private List<String> readDnsPair(EditText primary, EditText secondary) {
        ArrayList<String> addresses = new ArrayList<>(2);
        boolean primaryValid = appendDnsAddress(primary, addresses);
        boolean secondaryValid = appendDnsAddress(secondary, addresses);
        return primaryValid && secondaryValid ? List.copyOf(addresses) : null;
    }

    private boolean appendDnsAddress(EditText input, List<String> addresses) {
        try {
            List<String> parsed = DnsAddressParser.parseText(input.getText().toString());
            if (parsed.size() > 1) {
                throw new IllegalArgumentException();
            }
            input.setError(null);
            if (!parsed.isEmpty() && !addresses.contains(parsed.get(0))) {
                addresses.add(parsed.get(0));
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            input.setError(getString(R.string.invalid_dns));
            return false;
        }
    }

    private static void setDnsPair(
            List<String> addresses,
            EditText primary,
            EditText secondary
    ) {
        primary.setText(addresses.isEmpty() ? "" : addresses.get(0));
        secondary.setText(addresses.size() < 2 ? "" : addresses.get(1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
