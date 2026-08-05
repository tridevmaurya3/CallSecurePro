package com.tridev.callsecurepro.ui.common;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import com.tridev.callsecurepro.databinding.FragmentMainSectionBinding;

/**
 * Shared Fluent shell for the five primary application sections.
 * Each child fragment supplies only its own content and accent palette.
 */
public abstract class BaseMainSectionFragment extends Fragment {

    private FragmentMainSectionBinding binding;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentMainSectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int accentColor = ContextCompat.getColor(requireContext(), getAccentColorRes());
        int accentContainerColor = ContextCompat.getColor(
                requireContext(),
                getAccentContainerColorRes()
        );

        binding.sectionTitle.setText(getTitleRes());
        binding.sectionSubtitle.setText(getSubtitleRes());
        binding.sectionIcon.setText(getIconTextRes());
        binding.featureTitle.setText(getFeatureTitleRes());
        binding.featureBody.setText(getFeatureBodyRes());

        binding.sectionIcon.setTextColor(accentColor);
        ViewCompat.setBackgroundTintList(
                binding.sectionIcon,
                ColorStateList.valueOf(accentContainerColor)
        );

        binding.featureCard.setCardBackgroundColor(accentContainerColor);
        binding.featureCard.setStrokeColor(accentColor);

        binding.sectionStatusChip.setTextColor(accentColor);
        binding.sectionStatusChip.setChipBackgroundColor(
                ColorStateList.valueOf(accentContainerColor)
        );
    }

    @StringRes
    protected abstract int getTitleRes();

    @StringRes
    protected abstract int getSubtitleRes();

    @StringRes
    protected abstract int getIconTextRes();

    @StringRes
    protected abstract int getFeatureTitleRes();

    @StringRes
    protected abstract int getFeatureBodyRes();

    @ColorRes
    protected abstract int getAccentColorRes();

    @ColorRes
    protected abstract int getAccentContainerColorRes();

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
