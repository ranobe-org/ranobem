package in.atulpatare.ranobem.ui.settings;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import in.atulpatare.ranobem.BuildConfig;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.AppSettings;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.FragmentSettingsBinding;
import in.atulpatare.ranobem.ui.downloads.DownloadsActivity;
import in.atulpatare.ranobem.updates.ChapterUpdateScheduler;
import in.atulpatare.ranobem.utils.NotificationAccess;

/**
 * The settings tab: theme, new chapter notifications for the library, and links about the app.
 */
public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private ActivityResultLauncher<String> notificationPermission;
    // what the notification permission was asked for: a check by hand, or turning on automatic checks
    private boolean permissionForCheckNow;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (binding == null) return;
            if (granted && permissionForCheckNow) {
                checkNow();
            } else if (granted) {
                enableChapterUpdates();
            } else {
                notificationsBlocked();
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setUpHeader();
        setUpTheme();
        setUpChapterUpdates();
        setUpDownloads();
        setUpAbout();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        // notifications may have been turned on or off in system settings meanwhile
        syncChapterUpdates();
    }

    private void setUpHeader() {
        boolean pro = !Config.isFree();
        binding.editionBadge.setText(pro ? R.string.edition_pro : R.string.edition_free);
        binding.appVersion.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        binding.proApp.setVisibility(pro ? View.GONE : View.VISIBLE);
        binding.proApp.setOnClickListener(v -> openLink(Config.PRO_LINK));
        binding.footer.setText(getString(R.string.footer_version, getString(R.string.build_with), BuildConfig.VERSION_NAME));
    }

    private void setUpTheme() {
        int mode = AppSettings.themeMode(requireContext());
        // the row only labels the buttons below it
        binding.themeModeOption.setClickable(false);
        binding.themeModeOption.setIcon(themeIcon(mode));
        binding.themeModeToggle.check(themeButton(mode));
        binding.themeModeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int selected = checkedId == R.id.theme_light ? AppCompatDelegate.MODE_NIGHT_NO
                    : checkedId == R.id.theme_dark ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            // stored first: changing the mode recreates the activity, which reads it back
            AppSettings.setThemeMode(requireContext(), selected);
            AppCompatDelegate.setDefaultNightMode(selected);
        });
    }

    private static int themeButton(int mode) {
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return R.id.theme_light;
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return R.id.theme_dark;
        return R.id.theme_auto;
    }

    private static int themeIcon(int mode) {
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return R.drawable.ic_theme_mode_light;
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return R.drawable.ic_theme_mode_night;
        return R.drawable.ic_theme_mode_auto;
    }

    private void setUpChapterUpdates() {
        binding.chapterUpdatesOption.setOnClickListener(v -> {
            if (Config.isFree()) {
                showProUpsell();
                return;
            }
            boolean enabled = AppSettings.chapterUpdatesEnabled(requireContext());
            if (enabled && !NotificationAccess.enabled(requireContext())) {
                askToUnblock();
            } else if (enabled) {
                disableChapterUpdates();
            } else {
                requestAndEnable();
            }
        });
        binding.checkNowOption.showChevron();
        binding.checkNowOption.setOnClickListener(v -> requestAndCheckNow());
    }

    private void syncChapterUpdates() {
        if (Config.isFree()) {
            // automatic checks are Pro only, checking by hand is always there
            binding.chapterUpdatesOption.setChecked(false);
            binding.chapterUpdatesOption.setSubtitle(getString(R.string.settings_chapter_updates_pro_sub));
            binding.checkNowOption.setVisibility(View.VISIBLE);
            binding.checkNowDivider.setVisibility(View.VISIBLE);
            return;
        }
        boolean enabled = AppSettings.chapterUpdatesEnabled(requireContext());
        boolean canNotify = NotificationAccess.enabled(requireContext());
        binding.chapterUpdatesOption.setChecked(enabled);
        binding.chapterUpdatesOption.setSubtitle(getString(enabled && !canNotify
                ? R.string.settings_chapter_updates_blocked
                : R.string.settings_chapter_updates_sub));
        binding.checkNowOption.setVisibility(enabled ? View.VISIBLE : View.GONE);
        binding.checkNowDivider.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    // new chapters only show up as notifications, so a check without them would be silent
    private void requestAndCheckNow() {
        if (NotificationAccess.needsPermission(requireContext())) {
            permissionForCheckNow = true;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else if (!NotificationAccess.enabled(requireContext())) {
            notificationsBlocked();
        } else {
            checkNow();
        }
    }

    private void checkNow() {
        ChapterUpdateScheduler.checkNow(requireContext());
        Snackbar.make(binding.getRoot(), R.string.settings_check_now_started, Snackbar.LENGTH_LONG).show();
    }

    private void showProUpsell() {
        new MaterialAlertDialogBuilder(requireContext())
                .setIcon(R.drawable.ic_notifications)
                .setTitle(R.string.download_pro_title)
                .setMessage(R.string.chapter_updates_pro_message)
                .setPositiveButton(R.string.download_get_pro, (d, w) -> openLink(Config.PRO_LINK))
                .setNegativeButton(R.string.download_not_now, null)
                .show();
    }

    private void requestAndEnable() {
        if (NotificationAccess.needsPermission(requireContext())) {
            permissionForCheckNow = false;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else if (!NotificationAccess.enabled(requireContext())) {
            // blocked in system settings, asking again would do nothing
            notificationsBlocked();
        } else {
            enableChapterUpdates();
        }
    }

    private void enableChapterUpdates() {
        AppSettings.setChapterUpdatesEnabled(requireContext(), true);
        ChapterUpdateScheduler.schedule(requireContext());
        syncChapterUpdates();
        Snackbar.make(binding.getRoot(), R.string.settings_chapter_updates_on, Snackbar.LENGTH_SHORT).show();
    }

    private void disableChapterUpdates() {
        AppSettings.setChapterUpdatesEnabled(requireContext(), false);
        ChapterUpdateScheduler.cancel(requireContext());
        syncChapterUpdates();
        Snackbar.make(binding.getRoot(), R.string.settings_chapter_updates_off, Snackbar.LENGTH_SHORT).show();
    }

    // the permission was refused, or notifications are off in system settings
    private void notificationsBlocked() {
        boolean canAskAgain = NotificationAccess.needsPermission(requireContext())
                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
        Snackbar snackbar = Snackbar.make(binding.getRoot(), R.string.notifications_blocked, Snackbar.LENGTH_LONG);
        if (!canAskAgain) snackbar.setAction(R.string.open_settings, v -> NotificationAccess.openSettings(requireContext()));
        snackbar.show();
    }

    private void askToUnblock() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.notifications_blocked_title)
                .setMessage(R.string.settings_chapter_updates_blocked_message)
                .setPositiveButton(R.string.open_settings, (d, w) -> {
                    if (NotificationAccess.needsPermission(requireContext())
                            && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                    } else {
                        NotificationAccess.openSettings(requireContext());
                    }
                })
                .setNegativeButton(R.string.settings_chapter_updates_turn_off, (d, w) -> disableChapterUpdates())
                .show();
    }

    private void setUpDownloads() {
        int visibility = Config.isFree() ? View.GONE : View.VISIBLE;
        binding.downloadsTitle.setVisibility(visibility);
        binding.downloadsCard.setVisibility(visibility);
        binding.downloadsOption.showChevron();
        binding.downloadsOption.setOnClickListener(v -> startActivity(new Intent(requireContext(), DownloadsActivity.class)));
    }

    private void setUpAbout() {
        binding.roadmapOption.showChevron();
        binding.roadmapOption.setOnClickListener(v -> startActivity(new Intent(requireContext(), RoadmapActivity.class)));
        binding.discordLink.setOnClickListener(v -> openLink(Config.DISCORD_LINK));
        binding.updatesLink.setOnClickListener(v -> openLink(Config.GOOGLE_FORM_LINK));
        binding.keepAndroidOpenLink.setOnClickListener(v -> openLink(Config.KEEP_ANDROID_OPEN_LINK));
        binding.ranobeLink.setOnClickListener(v -> openLink(Config.RANOBE_LINK));
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.getRoot(), R.string.no_browser, Snackbar.LENGTH_LONG).show();
        }
    }
}
