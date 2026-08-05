package com.tridev.callsecurepro.ui.dial;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.ui.common.BaseMainSectionFragment;

public class DialFragment extends BaseMainSectionFragment {

    @Override
    protected int getTitleRes() {
        return R.string.dial_title;
    }

    @Override
    protected int getSubtitleRes() {
        return R.string.dial_subtitle;
    }

    @Override
    protected int getIconTextRes() {
        return R.string.dial_icon_text;
    }

    @Override
    protected int getFeatureTitleRes() {
        return R.string.dial_feature_title;
    }

    @Override
    protected int getFeatureBodyRes() {
        return R.string.dial_feature_body;
    }

    @Override
    protected int getAccentColorRes() {
        return R.color.csp_secondary;
    }

    @Override
    protected int getAccentContainerColorRes() {
        return R.color.csp_secondary_container;
    }
}
