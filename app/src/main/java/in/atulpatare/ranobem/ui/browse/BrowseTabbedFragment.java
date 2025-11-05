package in.atulpatare.ranobem.ui.browse;

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
import in.atulpatare.ranobem.databinding.FragmentBrowseTabbedBinding;

public class BrowseTabbedFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentBrowseTabbedBinding binding = FragmentBrowseTabbedBinding.inflate(inflater);

        List<Metadata> sources = new ArrayList<>();

        for (Class<?> source : SourceManager.getSources().values()) {
            try {
                Source s = (Source) source.newInstance();
                Metadata metadata = s.meta();
                sources.add(metadata);
            } catch (IllegalAccessException | java.lang.InstantiationException e) {
                throw new RuntimeException(e);
            }
        }

        Collections.reverse(sources);
        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(getChildFragmentManager(), sources);
        ViewPager viewPager = binding.viewPager;
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = binding.tabs;
        tabs.setupWithViewPager(viewPager);

        return binding.getRoot();
    }

    static class SectionsPagerAdapter extends FragmentStatePagerAdapter {

        private final List<Metadata> sources;

        public SectionsPagerAdapter(FragmentManager fm, List<Metadata> sources) {
            super(fm);
            this.sources = sources;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return BrowseFragment.newInstance(sources.get(position));
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