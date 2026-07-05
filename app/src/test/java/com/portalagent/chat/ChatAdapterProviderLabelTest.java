package com.portalagent.chat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ChatAdapterProviderLabelTest {

    @Test
    public void assistantBubbleUsesConfiguredProviderLabel() {
        Context context = RuntimeEnvironment.getApplication();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.assistant("answer"));
        ChatAdapter adapter = new ChatAdapter(messages);
        adapter.setAssistantLabel("Codex");

        ViewGroup parent = new FrameLayout(context);
        RecyclerView.ViewHolder holder =
            adapter.onCreateViewHolder(parent, adapter.getItemViewType(0));
        adapter.onBindViewHolder(holder, 0);

        TextView label = holder.itemView.findViewById(R.id.msg_sender_label);
        Assert.assertEquals("Codex", label.getText().toString());
    }

    @Test
    public void toolMessagesAreGroupedBehindExpandableHeader() {
        Context context = RuntimeEnvironment.getApplication();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("run command"));
        messages.add(ChatMessage.assistantStreaming("..."));
        ChatAdapter adapter = new ChatAdapter(messages);
        List<String> events = new ArrayList<>();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                events.add("insert:" + positionStart + ":" + itemCount);
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                events.add("change:" + positionStart + ":" + itemCount);
            }
        });

        adapter.addMessage(ChatMessage.toolUse("Bash", "{\"cmd\":\"pwd\"}"));
        events.clear();
        adapter.addMessage(ChatMessage.toolResult("Bash", "ok", "/workspace"));

        Assert.assertTrue(events.contains("insert:2:1"));
        Assert.assertTrue(events.contains("change:1:2"));

        ViewGroup parent = new FrameLayout(context);
        RecyclerView.ViewHolder holder =
            adapter.onCreateViewHolder(parent, adapter.getItemViewType(1));
        adapter.onBindViewHolder(holder, 1);

        TextView header = holder.itemView.findViewById(R.id.tool_header);
        TextView detail = holder.itemView.findViewById(R.id.tool_detail);
        Assert.assertEquals("工具调用 2 项：Bash ▸", header.getText().toString());
        Assert.assertEquals(View.GONE, detail.getVisibility());

        messages.get(1).toolGroupExpanded = true;
        adapter.onBindViewHolder(holder, 1);

        Assert.assertEquals("工具调用 2 项：Bash ▾", header.getText().toString());
        Assert.assertEquals(View.VISIBLE, detail.getVisibility());
        String expanded = detail.getText().toString();
        Assert.assertTrue(expanded.contains("🔧 调用 Bash"));
        Assert.assertTrue(expanded.contains("📥 Bash: ok"));
        Assert.assertFalse(expanded.contains("{\"cmd\":\"pwd\"}"));
        Assert.assertFalse(expanded.contains("/workspace"));
    }
}
