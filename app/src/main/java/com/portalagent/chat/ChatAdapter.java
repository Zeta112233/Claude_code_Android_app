package com.portalagent.chat;

import com.portalagent.ui.home.HomeFragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import io.noties.markwon.Markwon;

import java.util.List;

/** RecyclerView Adapter，渲染用户消息（右侧蓝色气泡）和 Claude 回复（左侧灰色气泡）。 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER         = 0;
    private static final int TYPE_ASSISTANT    = 1;
    private static final int TYPE_SYSTEM       = 2;
    private static final int TYPE_TOOL_USE     = 3;
    private static final int TYPE_TOOL_RESULT  = 4;

    private final List<ChatMessage> mMessages;
    private Markwon mMarkwon;
    private String mAssistantLabel = "Claude";
    private static final String THINKING_COLLAPSED_LABEL = "\u601d\u8003\u8fc7\u7a0b \u25b8";
    private static final String THINKING_COMPLETE_EXPANDED_LABEL = "\u601d\u8003\u8fc7\u7a0b \u25be";
    private static final String THINKING_EXPANDED_LABEL = "\u601d\u8003\u4e2d\u2026 \u25be";
    private static final String TOOL_COLLAPSED_SUFFIX = " \u25b8";
    private static final String TOOL_EXPANDED_SUFFIX = " \u25be";
    private static final String COPY_TOAST = "\u5df2\u590d\u5236";
    private static final int MAX_TOOL_HEADER_NAMES = 3;
    private static final int MAX_TOOL_HEADER_CHARS = 48;
    private static final int MAX_TOOL_DETAIL_LINES = 12;
    private static final int MAX_TOOL_LINE_CHARS = 180;

    public ChatAdapter(List<ChatMessage> messages) {
        this.mMessages = messages;
    }

    // -------------------------------------------------------------------------
    // Adapter 标准方法
    // -------------------------------------------------------------------------

    @Override
    public int getItemViewType(int position) {
        switch (mMessages.get(position).type) {
            case USER:         return TYPE_USER;
            case SYSTEM:       return TYPE_SYSTEM;
            case TOOL_USE:     return TYPE_TOOL_USE;
            case TOOL_RESULT:  return TYPE_TOOL_RESULT;
            default:           return TYPE_ASSISTANT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_msg_user, parent, false));
        } else if (viewType == TYPE_SYSTEM) {
            return new SystemViewHolder(inflater.inflate(R.layout.item_msg_system, parent, false));
        } else if (viewType == TYPE_TOOL_USE) {
            return new ToolViewHolder(inflater.inflate(R.layout.item_msg_tool_use, parent, false));
        } else if (viewType == TYPE_TOOL_RESULT) {
            return new ToolViewHolder(inflater.inflate(R.layout.item_msg_tool_result, parent, false));
        } else {
            return new AssistantViewHolder(inflater.inflate(R.layout.item_msg_assistant, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = mMessages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(msg.content);
        } else if (holder instanceof SystemViewHolder) {
            ((SystemViewHolder) holder).bind(msg, markwon(holder.itemView.getContext()));
        } else if (holder instanceof ToolViewHolder) {
            ((ToolViewHolder) holder).bind(msg, position, ChatAdapter.this);
        } else {
            ((AssistantViewHolder) holder).bind(msg, mAssistantLabel, markwon(holder.itemView.getContext()));
        }
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    // -------------------------------------------------------------------------
    // 辅助方法（HomeFragment 调用）
    // -------------------------------------------------------------------------

    /** 在列表末尾添加新消息并通知刷新。 */
    public void addMessage(ChatMessage msg) {
        boolean isToolMessage = ChatTurnOrdering.isToolMessage(msg);
        int index = isToolMessage
                ? ChatTurnOrdering.findToolInsertIndex(mMessages)
                : mMessages.size();
        mMessages.add(index, msg);
        notifyItemInserted(index);
        if (isToolMessage) {
            int start = toolGroupStart(index);
            int end = toolGroupEndExclusive(index);
            notifyItemRangeChanged(start, end - start);
        }
    }

    public void setAssistantLabel(String label) {
        String next = (label == null || label.trim().isEmpty()) ? "Claude" : label.trim();
        if (java.util.Objects.equals(mAssistantLabel, next)) return;
        mAssistantLabel = next;
        notifyDataSetChanged();
    }

    /**
     * 更新最后一条 ASSISTANT 消息的正文和思考内容（流式调用）。
     * 如果不存在 ASSISTANT 消息则自动创建。
     */
    public void updateLastAssistant(String content, String thinking) {
        if (thinking != null && !thinking.isEmpty()) {
            updateLastAssistantThinking(thinking);
        }
        if (content != null && !content.isEmpty()) {
            updateLastAssistantText(content);
        }
    }

    /** 兼容旧调用（无思考内容）。 */
    public void updateLastAssistant(String content) {
        updateLastAssistant(content, null);
    }

    /** 仅更新最后一条 ASSISTANT 的正文（thinking 字段保留不动）。 */
    public void updateLastAssistantText(String text) {
        int outputIndex = ChatTurnOrdering.findOutputIndex(mMessages);
        if (outputIndex >= 0) {
            ChatMessage msg = mMessages.get(outputIndex);
            if (!java.util.Objects.equals(msg.content, text)) {
                msg.content = text;
                notifyItemChanged(outputIndex);
            }
            return;
        }
        int index = ChatTurnOrdering.findOutputInsertIndex(mMessages);
        mMessages.add(index, ChatMessage.assistantStreaming(text));
        notifyItemInserted(index);
    }

    public void markLastAssistantOutputComplete() {
        int outputIndex = ChatTurnOrdering.findOutputIndex(mMessages);
        if (outputIndex < 0) return;
        ChatMessage msg = mMessages.get(outputIndex);
        if (msg != null && !msg.outputComplete) {
            msg.outputComplete = true;
            notifyItemChanged(outputIndex);
        }
    }

    /** 仅更新最后一条 ASSISTANT 的 thinking 字段。 */
    public void updateLastAssistantThinking(String thinking) {
        int thinkingIndex = ChatTurnOrdering.findThinkingIndex(mMessages);
        if (thinkingIndex >= 0) {
            ChatMessage msg = mMessages.get(thinkingIndex);
            if (!java.util.Objects.equals(msg.thinking, thinking)) {
                msg.thinking = thinking;
                notifyItemChanged(thinkingIndex);
            }
            return;
        }
        ChatMessage m = ChatMessage.assistant("");
        m.thinking = thinking;
        int index = ChatTurnOrdering.findThinkingInsertIndex(mMessages);
        mMessages.add(index, m);
        notifyItemInserted(index);
    }

    /** 折叠当前 turn 内所有 TOOL_USE / TOOL_RESULT 气泡的详情区。 */
    public void collapseAllToolDetailsInLastTurn() {
        // 从末尾向前，直到遇到 USER 消息为止
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = mMessages.get(i);
            if (msg.type == ChatMessage.Type.USER) break;
            if (ChatTurnOrdering.isToolMessage(msg)) {
                boolean changed = false;
                if (!msg.toolDetailCollapsed) {
                    msg.toolDetailCollapsed = true;
                    changed = true;
                }
                if (msg.toolGroupExpanded) {
                    msg.toolGroupExpanded = false;
                    changed = true;
                }
                if (changed) notifyItemChanged(i);
            }
        }
    }

    /** 回复完成后，将最后一条 ASSISTANT 消息的思考内容折叠。 */
    private int toolGroupStart(int position) {
        int start = position;
        while (start > 0 && ChatTurnOrdering.isToolMessage(mMessages.get(start - 1))) {
            start--;
        }
        return start;
    }

    private int toolGroupEndExclusive(int position) {
        int end = position + 1;
        while (end < mMessages.size() && ChatTurnOrdering.isToolMessage(mMessages.get(end))) {
            end++;
        }
        return end;
    }

    private String toolGroupHeader(int start, int end, boolean expanded) {
        int count = Math.max(1, end - start);
        String tools = toolNamesSummary(start, end);
        String prefix = "\u5de5\u5177\u8c03\u7528 " + count + " \u9879";
        if (!tools.isEmpty()) prefix += "\uff1a" + tools;
        return prefix
                + (expanded ? TOOL_EXPANDED_SUFFIX : TOOL_COLLAPSED_SUFFIX);
    }

    private String toolNamesSummary(int start, int end) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (int i = start; i < end; i++) {
            ChatMessage msg = mMessages.get(i);
            if (msg == null || msg.toolName == null || msg.toolName.trim().isEmpty()) continue;
            names.add(msg.toolName.trim());
        }
        if (names.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int index = 0;
        int remaining = 0;
        for (String name : names) {
            if (index >= MAX_TOOL_HEADER_NAMES) {
                remaining++;
                continue;
            }
            if (out.length() > 0) out.append(", ");
            out.append(name);
            index++;
        }
        if (remaining > 0) out.append(" +").append(remaining);
        return shortToolLine(out.toString(), MAX_TOOL_HEADER_CHARS);
    }

    private String toolGroupDetail(int start, int end) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (int i = start; i < end; i++) {
            ChatMessage msg = mMessages.get(i);
            if (msg == null) continue;
            if (shown >= MAX_TOOL_DETAIL_LINES) {
                int remaining = end - i;
                if (remaining > 0) {
                    if (out.length() > 0) out.append('\n');
                    out.append("... \u8fd8\u6709 ").append(remaining).append(" \u9879\u5de5\u5177\u8bb0\u5f55");
                }
                break;
            }
            if (out.length() > 0) out.append('\n');
            out.append(shown + 1).append(". ")
                    .append(shortToolLine(msg.content == null ? "" : msg.content, MAX_TOOL_LINE_CHARS));
            shown++;
        }
        return out.toString();
    }

    private static String shortToolLine(String value, int maxChars) {
        if (value == null) return "";
        String oneLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() <= maxChars) return oneLine;
        return oneLine.substring(0, Math.max(0, maxChars - 1)) + "\u2026";
    }

    private static void setRowHidden(View itemView, boolean hidden) {
        ViewGroup.LayoutParams lp = itemView.getLayoutParams();
        if (lp != null) {
            lp.height = hidden ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
            itemView.setLayoutParams(lp);
        }
        itemView.setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    public void collapseLastAssistantThinking() {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = mMessages.get(i);
            if (msg.type == ChatMessage.Type.ASSISTANT && msg.thinking != null
                    && !msg.thinkingCollapsed) {
                msg.thinkingCollapsed = true;
                notifyItemChanged(i);
                return;
            }
        }
    }

    /** 获取最后一条 ASSISTANT 消息，不存在返回 null。 */
    @Nullable
    public ChatMessage getLastAssistantMessage() {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            if (mMessages.get(i).type == ChatMessage.Type.ASSISTANT) {
                return mMessages.get(i);
            }
        }
        return null;
    }

    private Markwon markwon(Context context) {
        if (mMarkwon == null) {
            mMarkwon = Markwon.builder(context.getApplicationContext()).build();
        }
        return mMarkwon;
    }

    private static void setMarkdown(TextView textView, ChatMessage msg, Markwon markwon) {
        if (msg == null) {
            textView.setText("");
            return;
        }
        String raw = msg == null || msg.content == null ? "" : msg.content;
        if (!java.util.Objects.equals(msg.renderedMarkdownSource, raw) || msg.renderedMarkdown == null) {
            msg.renderedMarkdownSource = raw;
            msg.renderedMarkdown = markwon.toMarkdown(raw);
        }
        markwon.setParsedMarkdown(textView, msg.renderedMarkdown);
    }

    private static void installDeferredTextSelection(TextView textView) {
        textView.setTextIsSelectable(false);
        final boolean[] replayingLongClick = new boolean[] { false };
        textView.setOnLongClickListener(v -> {
            TextView tv = (TextView) v;
            if (replayingLongClick[0] || tv.isTextSelectable() || tv.getText().length() == 0) {
                return false;
            }
            tv.setTextIsSelectable(true);
            tv.requestFocus();
            replayingLongClick[0] = true;
            tv.post(() -> {
                try {
                    if (tv.isShown() && tv.isTextSelectable()) tv.performLongClick();
                } finally {
                    replayingLongClick[0] = false;
                }
            });
            return true;
        });
        textView.setOnFocusChangeListener((v, hasFocus) -> {
            TextView tv = (TextView) v;
            if (!hasFocus && tv.isTextSelectable()) tv.setTextIsSelectable(false);
        });
    }

    private static void resetDeferredTextSelection(TextView textView) {
        if (textView.isTextSelectable()) textView.setTextIsSelectable(false);
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;
        private final ImageButton mCopy;
        private String         mRawText = "";

        UserViewHolder(View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.msg_text);
            mCopy = itemView.findViewById(R.id.msg_copy);
            installDeferredTextSelection(mText);
            mCopy.setOnClickListener(v -> copyToClipboard(v, mRawText));
        }

        void bind(String content) {
            resetDeferredTextSelection(mText);
            mRawText = content == null ? "" : content;
            mText.setText(mRawText);
            mCopy.setVisibility(mRawText.trim().isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;
        private final TextView mLabel;
        private final View     mBubble;
        private final ImageButton mCopy;
        private String         mRawText = "";
        private final View     mThinkingContainer;
        private final TextView mThinkingHeader;
        private final TextView mThinkingText;

        AssistantViewHolder(View itemView) {
            super(itemView);
            mText              = itemView.findViewById(R.id.msg_text);
            mLabel             = itemView.findViewById(R.id.msg_sender_label);
            mBubble            = itemView.findViewById(R.id.msg_bubble);
            mCopy              = itemView.findViewById(R.id.msg_copy);
            mThinkingContainer = itemView.findViewById(R.id.thinking_container);
            mThinkingHeader    = itemView.findViewById(R.id.thinking_header);
            mThinkingText      = itemView.findViewById(R.id.thinking_text);
            installDeferredTextSelection(mText);
            installDeferredTextSelection(mThinkingText);
            mCopy.setOnClickListener(v -> copyToClipboard(v, mRawText));
        }

        void bind(ChatMessage msg, String assistantLabel, Markwon markwon) {
            resetDeferredTextSelection(mText);
            resetDeferredTextSelection(mThinkingText);
            mLabel.setText(assistantLabel == null || assistantLabel.isEmpty() ? "Claude" : assistantLabel);
            mRawText = msg.content == null ? "" : msg.content;
            if (mRawText.trim().isEmpty()) {
                mBubble.setVisibility(View.GONE);
                mText.setVisibility(View.GONE);
                mCopy.setVisibility(View.GONE);
            } else {
                mBubble.setVisibility(View.VISIBLE);
                mText.setVisibility(View.VISIBLE);
                mCopy.setVisibility(canCopy(msg) ? View.VISIBLE : View.GONE);
                if (msg.outputComplete) {
                    setMarkdown(mText, msg, markwon);
                } else {
                    mText.setText(mRawText);
                }
            }
            String thinking = msg.thinking;
            if (thinking == null || thinking.isEmpty()) {
                mThinkingContainer.setVisibility(View.GONE);
                return;
            }
            mThinkingContainer.setVisibility(View.VISIBLE);
            mThinkingText.setText(thinking);
            if (msg.thinkingCollapsed) {
                mThinkingText.setVisibility(View.GONE);
                mThinkingHeader.setText(THINKING_COLLAPSED_LABEL);
            } else {
                mThinkingText.setVisibility(View.VISIBLE);
                mThinkingHeader.setText(THINKING_EXPANDED_LABEL);
            }
            mThinkingContainer.setOnClickListener(v -> {
                if (mThinkingText.getVisibility() == View.VISIBLE) {
                    mThinkingText.setVisibility(View.GONE);
                    mThinkingHeader.setText(THINKING_COLLAPSED_LABEL);
                } else {
                    mThinkingText.setVisibility(View.VISIBLE);
                    mThinkingHeader.setText(msg.thinkingCollapsed
                            ? THINKING_COMPLETE_EXPANDED_LABEL : THINKING_EXPANDED_LABEL);
                }
            });
        }

        private boolean canCopy(ChatMessage msg) {
            return msg != null
                    && msg.outputComplete
                    && !mRawText.trim().isEmpty()
                    && !ChatTurnOrdering.isEmptyOrPlaceholder(mRawText);
        }
    }

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;
        private String         mRawText = "";

        SystemViewHolder(View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.msg_text);
            installDeferredTextSelection(mText);
        }

        void bind(ChatMessage msg, Markwon markwon) {
            resetDeferredTextSelection(mText);
            mRawText = msg == null || msg.content == null ? "" : msg.content;
            setMarkdown(mText, msg, markwon);
        }
    }

    static class ToolViewHolder extends RecyclerView.ViewHolder {
        private final TextView mHeader;
        private final TextView mDetail;
        private final View     mContainer;

        ToolViewHolder(View itemView) {
            super(itemView);
            mContainer = itemView.findViewById(R.id.tool_container);
            mHeader    = itemView.findViewById(R.id.tool_header);
            mDetail    = itemView.findViewById(R.id.tool_detail);
            installDeferredTextSelection(mDetail);
            itemView.setOnLongClickListener(v -> {
                CharSequence detail = (mDetail.getVisibility() == View.VISIBLE)
                        ? mDetail.getText() : null;
                String full = (detail == null || detail.length() == 0)
                        ? mHeader.getText().toString()
                        : mHeader.getText() + "\n" + detail;
                copyToClipboard(v, full);
                return true;
            });
        }

        void bind(ChatMessage msg, int position, ChatAdapter adapter) {
            resetDeferredTextSelection(mDetail);
            int start = adapter.toolGroupStart(position);
            int end = adapter.toolGroupEndExclusive(position);
            ChatMessage leader = adapter.mMessages.get(start);
            if (position != start) {
                setRowHidden(itemView, true);
                itemView.setOnClickListener(null);
                mContainer.setOnClickListener(null);
                mHeader.setOnClickListener(null);
                mDetail.setOnClickListener(null);
                return;
            }
            setRowHidden(itemView, false);
            boolean expanded = leader != null && leader.toolGroupExpanded;
            mHeader.setText(adapter.toolGroupHeader(start, end, expanded));
            mDetail.setText(expanded ? adapter.toolGroupDetail(start, end) : "");
            mDetail.setVisibility(expanded ? View.VISIBLE : View.GONE);
            View.OnClickListener toggle = v -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                int groupStart = adapter.toolGroupStart(pos);
                int groupEnd = adapter.toolGroupEndExclusive(pos);
                ChatMessage groupLeader = adapter.mMessages.get(groupStart);
                groupLeader.toolGroupExpanded = !groupLeader.toolGroupExpanded;
                adapter.notifyItemRangeChanged(groupStart, groupEnd - groupStart);
            };
            itemView.setOnClickListener(toggle);
            mContainer.setOnClickListener(toggle);
            mHeader.setOnClickListener(toggle);
            mDetail.setOnClickListener(toggle);
        }
    }

    private static void copyToClipboard(View v, String text) {
        ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("message", text));
            Toast.makeText(v.getContext(), COPY_TOAST, Toast.LENGTH_SHORT).show();
        }
    }
}
