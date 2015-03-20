package com.ywwxhz.processers;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.reflect.TypeToken;
import com.melnykov.fab.FloatingActionButton;
import com.ywwxhz.adapters.CommentListAdapter;
import com.ywwxhz.fragments.NewCommentFragment;
import com.ywwxhz.cnbetareader.R;
import com.ywwxhz.entitys.CommentItem;
import com.ywwxhz.entitys.CommentListObject;
import com.ywwxhz.entitys.ResponseObject;
import com.ywwxhz.widget.TranslucentStatus.TranslucentStatusHelper;
import com.ywwxhz.lib.handler.CommentListHandler;
import com.ywwxhz.lib.kits.FileCacheKit;
import com.ywwxhz.lib.kits.NetKit;
import com.ywwxhz.lib.kits.Toolkit;
import com.ywwxhz.lib.kits.UIKit;

import java.util.ArrayList;
import java.util.HashMap;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;

/**
 * Created by ywwxhz on 2014/11/2.
 */
public class NewsCommentProcesserImpl extends BaseProcesserImpl implements OnRefreshListener {
    public static final String SN_KEY = "key_sn";
    public static final String SID_KEY = "key_sid";
    public static final String TITLE_KEY = "key_title";
    private final CommentListHandler handler;
    private final CommentListAdapter mAdapter;
    private final TranslucentStatusHelper helper;
    private int sid;
    private String sn;
    private String token;
    private View mProgressBar;
    private Activity mContext;
    private ListView mListView;
    private TextView mTextView;
    private TextView mFoot;
    private FloatingActionButton actionButton;
    private PullToRefreshLayout mPullToRefreshLayout;

    public NewsCommentProcesserImpl(final Activity mContext, TranslucentStatusHelper helper) {
        this.mContext = mContext;
        Bundle bundle = mContext.getIntent().getExtras();
        if (!bundle.containsKey(SN_KEY) || !bundle.containsKey(TITLE_KEY) || !bundle.containsKey(SID_KEY)) {
            Toast.makeText(mContext, "缺失token", Toast.LENGTH_SHORT).show();
            mContext.finish();
        }
        mContext.setTitle("评论：" + bundle.getString(TITLE_KEY));
        this.sn = bundle.getString(SN_KEY);
        this.sid = bundle.getInt(SID_KEY);
        this.helper = helper;
        this.mProgressBar = mContext.findViewById(R.id.loading);
        this.mPullToRefreshLayout = new PullToRefreshLayout(mContext);
        this.mTextView = (TextView) mContext.findViewById(R.id.loadFail);
        this.mListView = (ListView) mContext.findViewById(android.R.id.list);
        this.helper.getOption().setConfigView(mListView);
        TextView type = (TextView) LayoutInflater.from(mContext).inflate(R.layout.type_head, mListView, false);
        type.setText("类型：全部评论");
        this.mFoot = new TextView(mContext);
        this.mFoot.setText("--- The End ---");
        this.mFoot.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        this.mFoot.setGravity(Gravity.CENTER);
        this.mFoot.setTextSize(16);
        int padding = UIKit.dip2px(mContext, 5);
        this.mFoot.setPadding(padding, padding, padding, padding);
        this.mFoot.setVisibility(View.GONE);
        this.mListView.addHeaderView(type, null, false);
        this.mListView.addFooterView(mFoot, null, false);
        this.actionButton = (FloatingActionButton) mContext.findViewById(R.id.action);
        this.mAdapter = new CommentListAdapter(mContext, new ArrayList<CommentItem>());
        this.handler = new CommentListHandler(this, new TypeToken<ResponseObject<CommentListObject>>() {
        });
        this.mTextView.setClickable(true);
        this.mListView.setAdapter(mAdapter);
        this.mProgressBar.setVisibility(View.GONE);
        this.actionButton.attachToListView(mListView);
        this.actionButton.setImageResource(R.mipmap.ic_edit);
        this.actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NewCommentFragment fragment = NewCommentFragment.getInstance(sid, "0", token);
                fragment.show(mContext.getFragmentManager(), "new comment");
            }
        });
        this.actionButton.setScaleX(0);
        this.actionButton.setScaleY(0);
        ActionBarPullToRefresh.from(mContext)
                .insertLayoutInto((ViewGroup) mContext.findViewById(android.R.id.content))
                .theseChildrenArePullable(mListView)
                .listener(this)
                .options(Options.create().scrollDistance(0.2f).refreshOnUp(true).build())
                .setup(mPullToRefreshLayout);
        mListView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mPullToRefreshLayout.setRefreshing(true);
                mProgressBar.setVisibility(View.VISIBLE);
                makeRequest();
            }
        }, 100);
        fixPos();
    }

    private void makeRequest() {
        if (mAdapter.getDataSet().size() == 0) {
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.setVisibility(View.GONE);
        }
        this.mListView.setOnItemClickListener(null);
        this.mTextView.setVisibility(View.GONE);
        NetKit.getInstance().getCommentBySnAndSid(sn, sid + "", handler);
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public Activity getActivity() {
        return mContext;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        fixPos();
    }

    public void callOnLoadingSuccess(CommentListObject commentListObject, boolean fromCache, boolean isClosed) {
        this.token = commentListObject.getToken();
        this.mAdapter.setToken(token);
        ArrayList<CommentItem> cmntlist = commentListObject.getCmntlist();
        HashMap<String, CommentItem> cmntstore = commentListObject.getCmntstore();
        for (CommentItem item : cmntlist) {
            StringBuilder sb = new StringBuilder();
            item.copy(cmntstore.get(item.getTid()));
            CommentItem parent = cmntstore.get(item.getPid());
            while (parent != null) {
                sb.append("//@");
                sb.append(parent.getName());
                sb.append(": [");
                sb.append(parent.getHost_name());
                sb.append("] ");
                sb.append(parent.getComment());
                parent = cmntstore.get(parent.getPid());
            }
            item.setRefContent(sb.toString());
        }
        ArrayList<CommentItem> hotcmntlist = commentListObject.getHotlist();
        for (CommentItem item : hotcmntlist) {
            StringBuilder sb = new StringBuilder();
            item.copy(cmntstore.get(item.getTid()));
            CommentItem parent = cmntstore.get(item.getPid());
            while (parent != null) {
                sb.append("//@");
                sb.append(parent.getName());
                sb.append(": [");
                sb.append(parent.getHost_name());
                sb.append("] ");
                sb.append(parent.getComment());
                parent = cmntstore.get(parent.getPid());
            }
            item.setRefContent(sb.toString());
        }
        if (cmntlist.size() > 0) { //针对加载缓存和普通访问
            this.mAdapter.setDataSet(cmntlist);
            if (!isClosed && !fromCache) {
                this.mAdapter.setEnable(true);
                this.actionButton.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        actionButton.setVisibility(View.VISIBLE);
                        actionButton.animate().scaleX(1).scaleY(1).setDuration(500).setInterpolator(new AccelerateDecelerateInterpolator()).start();
                    }
                }, 200);
                FileCacheKit.getInstance().putAsync(sid + "", Toolkit.getGson().toJson(commentListObject), "comment", null);
                Crouton.makeText(mContext, R.string.message_flush_success, Style.INFO).show();
            }
        } else if (commentListObject.getOpen() == 0) { //针对关平的新闻评论
            Crouton.makeText(mContext, R.string.message_comment_close, Style.ALERT).show();
            this.mAdapter.setEnable(false);
            this.mPullToRefreshLayout.setEnabled(false);
            if (callOnFailure(false, true)) {
                this.mTextView.setText(R.string.message_comment_close);
                this.mListView.setVisibility(View.GONE);
                this.mTextView.setVisibility(View.VISIBLE);
            }
        } else {//针对暂时无评论的情况
            Crouton.makeText(mContext, R.string.message_no_comment, Style.INFO).show();
            if (mAdapter.getCount() != 0) {
                this.mListView.setVisibility(View.GONE);
            }
            this.actionButton.postDelayed(new Runnable() {
                @Override
                public void run() {
                    actionButton.setVisibility(View.VISIBLE);
                    actionButton.animate().scaleX(1).scaleY(1).setDuration(500).setInterpolator(new AccelerateDecelerateInterpolator()).start();
                }
            }, 200);
            this.mTextView.setText(R.string.message_no_comment);
            this.mTextView.setVisibility(View.VISIBLE);
        }
    }

    public void setLoadFinish() {
        this.mProgressBar.setVisibility(View.GONE);
        this.mPullToRefreshLayout.setRefreshComplete();
        this.mAdapter.notifyDataSetChanged();
        if (mAdapter.getCount() > 0) {
            mFoot.setVisibility(View.VISIBLE);
        }
    }

    public boolean callOnFailure(boolean isWebChange, boolean isCommentClose) {
        CommentListObject commentListObject = FileCacheKit.getInstance().getAsObject(sid + "", "comment", new TypeToken<CommentListObject>() {
        });
        if (commentListObject != null) {
            callOnLoadingSuccess(commentListObject, true, isCommentClose);
            if (!isWebChange && !isCommentClose) {
                Crouton.makeText(mContext, R.string.message_load_from_cache, Style.ALERT).show();
                return true;
            } else return !isCommentClose;
        } else {
            if (!isCommentClose) {
                this.mTextView.setText(R.string.message_no_network);
                this.mTextView.setVisibility(View.VISIBLE);
                return false;
            } else {
                return true;
            }
        }
    }

    @Override
    public void onRefreshStarted(View view) {
        makeRequest();
    }

    private void fixPos() {
        int[] ints = helper.getInsertPixs(false);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) actionButton.getLayoutParams();
        int margin = layoutParams.leftMargin;
        layoutParams.rightMargin = margin + ints[2];
        layoutParams.bottomMargin = margin + ints[3];
        actionButton.setLayoutParams(layoutParams);
    }
}