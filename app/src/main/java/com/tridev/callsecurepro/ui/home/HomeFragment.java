package com.tridev.callsecurepro.ui.home;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.ui.common.BaseMainSectionFragment;

public class HomeFragment extends BaseMainSectionFragment {

    @Override
    protected int getTitleRes() {
        return R.string.home_title;
    }

    @Override
    protected int getSubtitleRes() {
        return R.string.home_subtitle;
    }

    @Override
    protected int getIconTextRes() {
        return R.string.home_icon_text;
    }

    @Override
    protected int getFeatureTitleRes() {
        return R.string.home_feature_title;
    }

    @Override
    protected int getFeatureBodyRes() {
        return R.string.home_feature_body;
    }

    @Override
    protected int getAccentColorRes() {
        return R.color.csp_primary;
    }

    @Override
    protected int getAccentContainerColorRes() {
        return R.color.csp_primary_container;
    }
}
