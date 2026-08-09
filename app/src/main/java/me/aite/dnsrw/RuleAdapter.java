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

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.RuleHolder> {
    record Item(String id, String label, DnsConfig.Rule rule) {
    }

    interface DragStarter {
        void start(RecyclerView.ViewHolder holder);
    }

    private final boolean wifi;
    private final Consumer<Item> edit;
    private final Consumer<Item> delete;
    private final DragStarter dragStarter;
    private final ArrayList<Item> items = new ArrayList<>();
    private boolean orderChanged;

    RuleAdapter(
            boolean wifi,
            Consumer<Item> edit,
            Consumer<Item> delete,
            DragStarter dragStarter
    ) {
        this.wifi = wifi;
        this.edit = edit;
        this.delete = delete;
        this.dragStarter = dragStarter;
    }

    void setItems(List<Item> updated) {
        if (items.equals(updated)) {
            return;
        }
        int previousSize = items.size();
        items.clear();
        notifyItemRangeRemoved(0, previousSize);
        items.addAll(updated);
        notifyItemRangeInserted(0, items.size());
        orderChanged = false;
    }

    boolean move(int from, int to) {
        if (from == to) {
            return false;
        }
        items.add(to, items.remove(from));
        orderChanged = true;
        notifyItemMoved(from, to);
        return true;
    }

    boolean consumeOrderChanged() {
        boolean changed = orderChanged;
        orderChanged = false;
        return changed;
    }

    List<String> orderedIds() {
        ArrayList<String> ids = new ArrayList<>(items.size());
        for (Item item : items) {
            ids.add(item.id());
        }
        return List.copyOf(ids);
    }

    @NonNull
    @Override
    public RuleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rule_card, parent, false);
        return new RuleHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleHolder holder, int position) {
        Item item = items.get(position);
        holder.title.setText(wifi ? item.label() : formatMobileLabel(item.label()));
        holder.edit.setOnClickListener(view -> edit.accept(item));
        holder.delete.setOnClickListener(view -> delete.accept(item));
        holder.itemView.setOnLongClickListener(view -> {
            dragStarter.start(holder);
            return true;
        });

        String[] modes = holder.itemView.getResources().getStringArray(R.array.rule_modes);
        String summary = modes[item.rule().mode().ordinal()];
        if (item.rule().mode() == DnsConfig.Mode.CUSTOM) {
            summary += "\n" + DnsAddressParser.format(item.rule().customDns());
        }
        holder.summary.setText(summary);

        if (wifi) {
            holder.identity.setVisibility(View.GONE);
        } else {
            holder.identity.setText(item.id());
            holder.identity.setTextIsSelectable(true);
            holder.identity.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static CharSequence formatMobileLabel(String label) {
        int slotStart = label.indexOf("卡槽 ");
        if (slotStart < 0) {
            return label;
        }
        int numberStart = slotStart + 3;
        int numberEnd = numberStart;
        while (numberEnd < label.length() && Character.isDigit(label.charAt(numberEnd))) {
            numberEnd++;
        }
        if (numberStart == numberEnd) {
            return label;
        }
        SpannableString text = new SpannableString(label);
        text.setSpan(
                new TypefaceSpan("sans-serif-monospace"),
                numberStart,
                numberEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return text;
    }

    static final class RuleHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView summary;
        final TextView identity;
        final MaterialButton edit;
        final MaterialButton delete;

        RuleHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.rule_title);
            summary = itemView.findViewById(R.id.rule_summary);
            identity = itemView.findViewById(R.id.rule_identity);
            edit = itemView.findViewById(R.id.rule_edit);
            delete = itemView.findViewById(R.id.rule_delete);
        }
    }
}
