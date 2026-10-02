package in.atulpatare.ranobem.ui.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.databinding.ViewSettingOptionBinding;

/**
 * A settings row: icon, title, an optional subtitle, and a switch, chevron or external link
 * marker at the end.
 */
public class SettingOptionView extends LinearLayout {
    private final ViewSettingOptionBinding binding;

    public SettingOptionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        binding = ViewSettingOptionBinding.inflate(LayoutInflater.from(context), this, true);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SettingOptionView, 0, 0);
        try {
            binding.icon.setImageResource(a.getResourceId(R.styleable.SettingOptionView_settingIcon, R.drawable.ic_settings));
            if (!a.getBoolean(R.styleable.SettingOptionView_settingIconTinted, true)) ImageViewCompat.setImageTintList(binding.icon, null);
            binding.title.setText(a.getString(R.styleable.SettingOptionView_settingTitle));
            setSubtitle(a.getString(R.styleable.SettingOptionView_settingSubtitle));
            boolean external = a.getBoolean(R.styleable.SettingOptionView_settingExternal, false);
            binding.external.setVisibility(external ? View.VISIBLE : View.GONE);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        binding.getRoot().setOnClickListener(l);
    }

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        // may run from a super constructor, before the row is inflated
        if (binding == null) return;
        binding.getRoot().setClickable(clickable);
        binding.getRoot().setFocusable(clickable);
        // drop the ripple so a label-only row doesn't look tappable
        if (!clickable) binding.getRoot().setBackground(null);
    }

    public void setSubtitle(@Nullable CharSequence subtitle) {
        binding.subtitle.setText(subtitle);
        binding.subtitle.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
    }

    public void setIcon(int resource) {
        binding.icon.setImageResource(resource);
    }

    public void showChevron() {
        binding.chevron.setVisibility(View.VISIBLE);
    }

    public void setChecked(boolean checked) {
        binding.toggle.setVisibility(View.VISIBLE);
        binding.toggle.setChecked(checked);
        // the switch itself isn't focusable, the row speaks for it
        ViewCompat.setStateDescription(binding.getRoot(), getContext().getString(checked ? R.string.on : R.string.off));
    }
}
