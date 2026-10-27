package com.alan.moviebox;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

// 全屏播放器。遥控器：确定键暂停 / 继续，左右键快退快进 10 秒（按住加速），
// 上键上一集，下键下一集，返回键退出。一集播完自动播下一集。
public class PlayerActivity extends UiActivity {
    static final String DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0 Safari/537.36";

    SiteSpec spec;
    String flag, title;
    String[] names, ids;
    int index;

    ExoPlayer player;
    SurfaceView surface;
    ProgressBar spinner, bar;
    TextView msgV, titleV, timeV;
    View topBar, bottomBar;

    int token = 0;        // 每次换集加一，用来丢掉过期的「取地址」结果
    long resumePos = 0;   // 切到后台再回来时，接着上次的位置播

    final Runnable hide = () -> {
        if (player != null && player.isPlaying()) {
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
        }
    };

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            h.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        spec = SiteSpec.parse(getIntent().getStringExtra("spec"));
        flag = nz(getIntent().getStringExtra("flag"));
        title = nz(getIntent().getStringExtra("title"));
        names = getIntent().getStringArrayExtra("names");
        ids = getIntent().getStringArrayExtra("ids");
        index = getIntent().getIntExtra("index", 0);
        if (names == null || ids == null || ids.length == 0) {
            toast("没有可播放的内容");
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(px(64), px(64));
        sp.gravity = Gravity.CENTER;
        root.addView(spinner, sp);

        msgV = tv("", 22, MainActivity.C_TEXT, false);
        msgV.setGravity(Gravity.CENTER);
        msgV.setLineSpacing(0, 1.3f);
        msgV.setPadding(px(80), 0, px(80), 0);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-1, -2);
        mp.gravity = Gravity.CENTER;
        mp.topMargin = px(110);
        root.addView(msgV, mp);

        // 顶部：片名和集数
        titleV = tv("", 24, MainActivity.C_TEXT, true);
        titleV.setShadowLayer(6, 0, 0, 0xFF000000);
        titleV.setPadding(px(48), px(28), px(48), px(40));
        titleV.setBackground(fade(true));
        topBar = titleV;
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2);
        tp.gravity = Gravity.TOP;
        root.addView(titleV, tp);

        // 底部：进度条、时间、操作提示
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(px(48), px(40), px(48), px(24));
        bottom.setBackground(fade(false));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bottom.addView(bar, new LinearLayout.LayoutParams(-1, px(8)));
        timeV = tv("", 18, MainActivity.C_TEXT, false);
        bottom.addView(timeV, lp(-2, -2, 0, 10));
        TextView hint = tv("确定 暂停 / 继续    左右 快退 / 快进    上下 换集    返回 退出",
                15, MainActivity.C_MUTED, false);
        bottom.addView(hint, lp(-2, -2, 0, 6));
        bottomBar = bottom;
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2);
        bp.gravity = Gravity.BOTTOM;
        root.addView(bottom, bp);

        setContentView(root);
    }

    String nz(String s) {
        return s == null ? "" : s;
    }

    // 顶部或底部的渐变暗色，让文字在任何画面上都看得清
    GradientDrawable fade(boolean top) {
        return new GradientDrawable(
                top ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xCC000000, 0x00000000});
    }

    // ---------- 生命周期：进入前台时创建播放器，切到后台就释放 ----------

    @Override
    protected void onStart() {
        super.onStart();
        if (ids == null || ids.length == 0) return;
        initPlayer();
        loadEpisode(index);
        h.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        h.removeCallbacks(tick);
        h.removeCallbacks(hide);
        token++;
        if (player != null) {
            resumePos = player.getCurrentPosition();
            player.release();
            player = null;
        }
    }

    void initPlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(this).build();
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                spinner.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_READY) {
                    msgV.setVisibility(View.GONE);
                    showOverlay();
                } else if (state == Player.STATE_ENDED) {
                    resumePos = 0;
                    if (index + 1 < ids.length) {
                        loadEpisode(index + 1);
                    } else {
                        showMsg("播放完了");
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                spinner.setVisibility(View.GONE);
                Throwable c = error.getCause();
                String cause = c == null ? "" : "\n" + c.getClass().getSimpleName()
                        + (c.getMessage() != null ? "：" + c.getMessage() : "");
                showMsg("播放失败：" + error.getErrorCodeName() + cause + "\n\n按上下键换集，返回键退出");
                showOverlay();
            }
        });
    }

    // ---------- 换集：先把「集的编号」换成真实地址，再交给播放器 ----------

    void loadEpisode(int i) {
        if (i < 0 || i >= ids.length) {
            toast(i < 0 ? "已经是第一集" : "已经是最后一集");
            return;
        }
        index = i;
        final int my = ++token;
        titleV.setText(names.length > 1 || ids.length > 1 ? title + "  ·  " + names[i] : title);
        spinner.setVisibility(View.VISIBLE);
        showMsg("正在获取播放地址…");
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        showOverlay();

        final String id = ids[i];
        Net.POOL.execute(() -> {
            try {
                final Catalog.PlayInfo info = Catalog.play(getApplicationContext(), spec, flag, id);
                Net.MAIN.post(() -> {
                    if (my != token || isFinishing() || player == null) return;
                    startMedia(info);
                });
            } catch (Throwable e) {
                final String m = errText(e);
                Net.MAIN.post(() -> {
                    if (my != token || isFinishing()) return;
                    spinner.setVisibility(View.GONE);
                    showMsg("获取播放地址失败：\n" + m + "\n\n按上下键换集，返回键退出");
                });
            }
        });
    }

    void startMedia(Catalog.PlayInfo info) {
        Map<String, String> hdr = new HashMap<>(info.headers);
        String ua = DEFAULT_UA;
        for (Iterator<Map.Entry<String, String>> it = hdr.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            if (e.getKey().equalsIgnoreCase("user-agent")) {  // 播放器要单独设置 User-Agent
                ua = e.getValue();
                it.remove();
            }
        }
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(ua)
                .setDefaultRequestProperties(hdr);

        MediaItem.Builder mb = new MediaItem.Builder().setUri(info.url);
        if (info.url.contains(".m3u8")) mb.setMimeType(MimeTypes.APPLICATION_M3U8);

        msgV.setVisibility(View.GONE);
        player.setMediaSource(new DefaultMediaSourceFactory(f).createMediaSource(mb.build()));
        player.prepare();
        player.setPlayWhenReady(true);
        if (resumePos > 0) {
            player.seekTo(resumePos);
            resumePos = 0;
        }
    }

    // ---------- 遥控器 ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (player == null) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (event.getRepeatCount() == 0) {
                    player.setPlayWhenReady(!player.getPlayWhenReady());
                    showOverlay();
                    updateProgress();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                player.setPlayWhenReady(true);
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                player.setPlayWhenReady(false);
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-10000L * (1 + Math.min(event.getRepeatCount() / 3, 5)));
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(10000L * (1 + Math.min(event.getRepeatCount() / 3, 5)));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (event.getRepeatCount() == 0) {
                    resumePos = 0;
                    loadEpisode(index - 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (event.getRepeatCount() == 0) {
                    resumePos = 0;
                    loadEpisode(index + 1);
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    void seekBy(long delta) {
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        long target = Math.max(0, pos + delta);
        if (dur != C.TIME_UNSET && dur > 0) target = Math.min(target, Math.max(0, dur - 1000));
        player.seekTo(target);
        showOverlay();
        updateProgress();
    }

    // ---------- 界面 ----------

    void showMsg(String s) {
        msgV.setText(s);
        msgV.setVisibility(View.VISIBLE);
    }

    // 显示顶部和底部的信息栏，4 秒后自动收起（暂停时一直显示）
    void showOverlay() {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        h.removeCallbacks(hide);
        h.postDelayed(hide, 4000);
    }

    void updateProgress() {
        if (player == null) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        boolean known = dur != C.TIME_UNSET && dur > 0;
        bar.setProgress(known ? (int) Math.min(1000, pos * 1000 / dur) : 0);
        String t = fmt(pos) + (known ? " / " + fmt(dur) : "");
        if (!player.getPlayWhenReady()) t += "     已暂停";
        timeV.setText(t);
    }

    static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        long hh = s / 3600;
        long mm = (s % 3600) / 60;
        long ss = s % 60;
        return hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, mm, ss)
                : String.format(Locale.US, "%d:%02d", mm, ss);
    }
}
