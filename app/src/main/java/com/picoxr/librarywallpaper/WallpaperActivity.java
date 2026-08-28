package com.picoxr.librarywallpaper;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class WallpaperActivity extends Activity {
    private static final int PICK_IMAGE = 100;
    private static final int MAIN_BACKGROUND = Color.rgb(30, 30, 30);
    private static final int CONTENT_BACKGROUND = Color.rgb(41, 41, 41);
    private static final int CARD_BACKGROUND = Color.rgb(50, 50, 50);
    private static final int CONTROL_BACKGROUND = Color.rgb(61, 61, 61);
    private static final int PRIMARY_GREEN = Color.rgb(37, 203, 1);
    private static final int ERROR_RED = Color.rgb(242, 69, 63);

    private WallpaperEditorView editor;
    private TextView status;
    private View libraryTarget;
    private View settingsTarget;
    private Uri pendingImage;
    private Bitmap pendingBitmap;
    private WallpaperTransform libraryTransform;
    private WallpaperTransform settingsTransform;
    private WallpaperTarget activeTarget = WallpaperTarget.LIBRARY;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.localizedContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        libraryTransform = WallpaperStore.loadConfig(this, WallpaperTarget.LIBRARY).transform;
        settingsTransform = WallpaperStore.loadConfig(this, WallpaperTarget.SETTINGS).transform;
        setContentView(createContent());
        updateStatus();
        updateTargetCards();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingBitmap != null) {
            pendingBitmap.recycle();
            pendingBitmap = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        pendingImage = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(pendingImage,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        if (pendingBitmap != null) {
            pendingBitmap.recycle();
        }
        pendingBitmap = ImageLoader.load(this, pendingImage);
        if (pendingBitmap == null) {
            pendingImage = null;
            status.setText(R.string.status_open_failed);
            return;
        }
        libraryTransform = WallpaperTransform.centered();
        settingsTransform = WallpaperTransform.centered();
        editor.setBitmap(pendingBitmap);
        showTarget(WallpaperTarget.LIBRARY);
        status.setText(R.string.status_ready);
    }

    private View createContent() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(MAIN_BACKGROUND);
        LogicalWorkspaceLayout canvas = new LogicalWorkspaceLayout(this);
        screen.addView(canvas, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout workspace = new FrameLayout(this);
        workspace.setPadding(20, 20, 20, 20);
        workspace.setBackground(rounded(CONTENT_BACKGROUND, 24));
        canvas.addView(workspace, new FrameLayout.LayoutParams(
                WallpaperState.EDITOR_WIDTH, WallpaperState.EDITOR_HEIGHT));

        TextView badge = label("W", 28, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setBackground(rounded(PRIMARY_GREEN, 12));
        workspace.addView(badge, at(40, 30, 64, 64));

        TextView title = label(R.string.studio_title, 27, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        workspace.addView(title, at(122, 28, 500, 42));
        TextView subtitle = label(R.string.studio_subtitle, 15, Color.rgb(185, 185, 185));
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        workspace.addView(subtitle, at(122, 70, 500, 28));

        Button language = actionButton(getString(R.string.language_button), CONTROL_BACKGROUND, Color.WHITE);
        language.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14);
        language.setPadding(12, 0, 12, 0);
        language.setOnClickListener(view -> showLanguageDialog());
        workspace.addView(language, at(640, 38, 116, 48));

        status = label("", 14, Color.rgb(190, 205, 218));
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(16, 6, 16, 6);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setBackground(rounded(CARD_BACKGROUND, 12));
        workspace.addView(status, at(770, 30, 470, 64));

        libraryTarget = targetCard(R.string.target_library, R.string.target_library_detail,
                WallpaperTarget.LIBRARY);
        workspace.addView(libraryTarget, at(40, 126, 280, 100));
        settingsTarget = targetCard(R.string.target_settings, R.string.target_settings_detail,
                WallpaperTarget.SETTINGS);
        workspace.addView(settingsTarget, at(40, 240, 280, 100));

        TextView editHeading = label(R.string.preview_heading, 18, Color.WHITE);
        editHeading.setTypeface(null, Typeface.BOLD);
        workspace.addView(editHeading, at(350, 122, 540, 34));
        TextView editHelp = label(R.string.preview_help, 14, Color.rgb(180, 180, 180));
        editHelp.setSingleLine(true);
        editHelp.setEllipsize(TextUtils.TruncateAt.END);
        workspace.addView(editHelp, at(350, 156, 540, 28));

        editor = new WallpaperEditorView(this);
        editor.setPadding(20, 16, 20, 16);
        editor.setBackground(rounded(Color.rgb(24, 24, 24), 12));
        editor.setListener(transform -> {
            if (activeTarget == WallpaperTarget.LIBRARY) {
                libraryTransform = transform;
            } else {
                settingsTransform = transform;
            }
        });
        workspace.addView(editor, at(350, 190, 560, 378));

        TextView ratio = label(R.string.content_area_ratio, 14, Color.rgb(125, 191, 255));
        ratio.setGravity(Gravity.CENTER);
        workspace.addView(ratio, at(350, 574, 560, 28));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(18, 14, 18, 12);
        info.setBackground(rounded(CARD_BACKGROUND, 12));
        TextView infoTitle = label(R.string.actions_heading, 16, Color.WHITE);
        infoTitle.setTypeface(null, Typeface.BOLD);
        info.addView(infoTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 28));
        TextView infoText = label(R.string.actions_detail, 13, Color.rgb(185, 185, 185));
        infoText.setMaxLines(3);
        infoText.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(infoText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56));
        workspace.addView(info, at(930, 122, 310, 122));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(14, 12, 14, 12);
        actions.setBackground(rounded(CARD_BACKGROUND, 12));
        Button choose = actionButton(getString(R.string.action_choose_image), CONTROL_BACKGROUND, Color.WHITE);
        choose.setOnClickListener(view -> chooseImage());
        actions.addView(choose, actionParams());
        Button restore = actionButton(getString(R.string.action_restore), ERROR_RED, Color.WHITE);
        restore.setOnClickListener(view -> chooseRestoreRange());
        actions.addView(restore, actionParams());
        Button apply = actionButton(getString(R.string.action_apply), PRIMARY_GREEN, Color.BLACK);
        apply.setTypeface(null, Typeface.BOLD);
        apply.setOnClickListener(view -> chooseApplyRange());
        actions.addView(apply, actionParams());
        workspace.addView(actions, at(40, 616, 1200, 80));
        return screen;
    }

    private View targetCard(int title, int detail, WallpaperTarget target) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(18, 14, 18, 12);
        card.setOnClickListener(view -> showTarget(target));
        TextView heading = label(title, 17, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30));
        TextView caption = label(detail, 13, Color.rgb(190, 190, 190));
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(caption, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 26));
        return card;
    }

    private void updateTargetCards() {
        updateTargetCard(libraryTarget, activeTarget == WallpaperTarget.LIBRARY);
        updateTargetCard(settingsTarget, activeTarget == WallpaperTarget.SETTINGS);
    }

    private void updateTargetCard(View card, boolean selected) {
        card.setBackground(rounded(selected ? Color.rgb(54, 87, 47) : CARD_BACKGROUND, 12));
    }

    private TextView label(int text, int size, int color) {
        return label(getString(text), size, color);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    private Button actionButton(String text, int background, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(18, 0, 18, 0);
        button.setBackground(rounded(background, 12));
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private FrameLayout.LayoutParams at(int left, int top, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        return params;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 52, 1f);
        params.setMargins(6, 0, 6, 0);
        return params;
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_IMAGE);
    }

    private void chooseApplyRange() {
        if (pendingImage == null || pendingBitmap == null) {
            status.setText(R.string.status_choose_first);
            return;
        }
        showRangeDialog(getString(R.string.dialog_apply_title), new String[]{
                getString(R.string.dialog_apply_library),
                getString(R.string.dialog_apply_settings),
                getString(R.string.dialog_apply_both)
        }, PRIMARY_GREEN, this::apply);
    }

    private void apply(int range) {
        status.setText(R.string.status_saving);
        new Thread(() -> {
            boolean librarySaved = range != 0 && range != 2;
            boolean settingsSaved = range != 1 && range != 2;
            if (range == 0 || range == 2) {
                librarySaved = WallpaperStore.saveImage(this, pendingImage, WallpaperTarget.LIBRARY);
                WallpaperStore.saveConfig(this, WallpaperTarget.LIBRARY,
                        new WallpaperConfig(librarySaved, libraryTransform));
            }
            if (range == 1 || range == 2) {
                settingsSaved = WallpaperStore.saveImage(this, pendingImage, WallpaperTarget.SETTINGS);
                WallpaperStore.saveConfig(this, WallpaperTarget.SETTINGS,
                        new WallpaperConfig(settingsSaved, settingsTransform));
            }
            boolean completed = librarySaved && settingsSaved;
            runOnUiThread(() -> {
                if (!completed) {
                    status.setText(R.string.status_save_failed);
                    return;
                }
                restartTargets(range);
                status.setText(R.string.status_applied);
            });
        }).start();
    }

    private void chooseRestoreRange() {
        showRangeDialog(getString(R.string.dialog_restore_title), new String[]{
                getString(R.string.dialog_restore_library),
                getString(R.string.dialog_restore_settings),
                getString(R.string.dialog_restore_both)
        }, ERROR_RED, this::restore);
    }

    private void restore(int range) {
        boolean success = true;
        if (range == 0 || range == 2) {
            success &= WallpaperStore.restore(this, WallpaperTarget.LIBRARY);
        }
        if (range == 1 || range == 2) {
            success &= WallpaperStore.restore(this, WallpaperTarget.SETTINGS);
        }
        if (success) {
            restartTargets(range);
            status.setText(R.string.status_restored);
        } else {
            status.setText(R.string.status_restore_failed);
        }
    }

    private void showLanguageDialog() {
        showRangeDialog(getString(R.string.language_title), new String[]{
                getString(R.string.language_chinese),
                getString(R.string.language_english),
                getString(R.string.language_russian)
        }, PRIMARY_GREEN, range -> {
            String language = range == 0 ? AppLanguage.CHINESE
                    : range == 1 ? AppLanguage.ENGLISH : AppLanguage.RUSSIAN;
            if (!language.equals(AppLanguage.read(this))) {
                AppLanguage.save(this, language);
                recreate();
            }
        });
    }

    private void showRangeDialog(String title, String[] options, int accent, RangeAction action) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(30, 28, 30, 28);
        sheet.setBackground(rounded(CONTENT_BACKGROUND, 24));
        TextView heading = label(title, 28, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        sheet.addView(heading, new LinearLayout.LayoutParams(620, 58));
        for (int index = 0; index < options.length; index++) {
            int range = index;
            Button option = actionButton(options[index], index == 2 ? accent : CONTROL_BACKGROUND, Color.WHITE);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
            option.setPadding(22, 0, 22, 0);
            option.setOnClickListener(view -> {
                dialog.dismiss();
                action.run(range);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(620, 68);
            params.topMargin = 12;
            sheet.addView(option, params);
        }
        dialog.setContentView(sheet);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(680, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    private void restartTargets(int range) {
        new Thread(() -> {
            if (range == 0 || range == 2) {
                RootShell.execute("am force-stop com.pvr.appmanager");
                RootShell.execute("am start -a pvr.intent.action.STORE_APP");
            }
            if (range == 1 || range == 2) {
                RootShell.execute("am force-stop com.picovr.settings");
                RootShell.execute("am start -a pui.settings.action.SETTINGS");
            }
        }).start();
    }

    private void showTarget(WallpaperTarget target) {
        activeTarget = target;
        editor.setTarget(target, target == WallpaperTarget.LIBRARY ? libraryTransform : settingsTransform);
        updateTargetCards();
    }

    private void updateStatus() {
        WallpaperConfig library = WallpaperStore.loadConfig(this, WallpaperTarget.LIBRARY);
        WallpaperConfig settings = WallpaperStore.loadConfig(this, WallpaperTarget.SETTINGS);
        status.setText(!library.enabled && !settings.enabled
                ? R.string.status_default_active : R.string.status_custom_active);
    }

    private interface RangeAction {
        void run(int range);
    }
}
