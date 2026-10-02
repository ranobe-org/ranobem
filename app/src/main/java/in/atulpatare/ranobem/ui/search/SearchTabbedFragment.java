package in.atulpatare.ranobem.ui.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.atulpatare.core.models.Metadata;
import in.atulpatare.core.sources.Source;
import in.atulpatare.core.sources.SourceManager;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.databinding.FragmentSearchTabbedBinding;

public class SearchTabbedFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentSearchTabbedBinding binding = FragmentSearchTabbedBinding.inflate(inflater);

        List<Metadata> sources = new ArrayList<>();

        for (Class<?> source : SourceManager.getSources().values()) {
            try {
                Source s = (Source) source.newInstance();
                Metadata metadata = s.meta();
                if (metadata.isSearchSupported) {
                    sources.add(metadata);
                }
            } catch (IllegalAccessException | java.lang.InstantiationException e) {
                throw new RuntimeException(e);
            }
        }

        Collections.reverse(sources);

        // opened pre-filtered (e.g. a genre tapped on a details page): jump to that source's tab
        Bundle args = getArguments();
        int targetSource = args != null ? args.getInt(Config.KEY_SOURCE_ID, -1) : -1;
        int targetIndex = -1;
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).sourceId == targetSource) targetIndex = i;
        }

        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(getChildFragmentManager(), sources, targetSource, args);
        ViewPager viewPager = binding.viewPager;
        viewPager.setAdapter(sectionsPagerAdapter);
        if (targetIndex >= 0 && savedInstanceState == null) viewPager.setCurrentItem(targetIndex, false);
        TabLayout tabs = binding.tabs;
        tabs.setupWithViewPager(viewPager);
        tabs.setVisibility(sources.size() > 1 ? View.VISIBLE : View.GONE);

        return binding.getRoot();
    }

    static class SectionsPagerAdapter extends FragmentStatePagerAdapter {

        private final List<Metadata> sources;
        private final int filteredSourceId;
        private final Bundle filters;

        public SectionsPagerAdapter(FragmentManager fm, List<Metadata> sources, int filteredSourceId, Bundle filters) {
            super(fm);
            this.sources = sources;
            this.filteredSourceId = filteredSourceId;
            this.filters = filters;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Metadata meta = sources.get(position);
            if (meta.sourceId == filteredSourceId && filters != null) {
                return SearchFragment.newInstance(meta, filters.getString(Config.KEY_AUTHOR), filters.getString(Config.KEY_GENRE));
            }
            return SearchFragment.newInstance(meta);
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return sources.get(position).name;
        }

        @Override
        public int getCount() {
            return sources.size();
        }
    }
}