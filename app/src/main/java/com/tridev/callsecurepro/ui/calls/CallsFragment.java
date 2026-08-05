package com.tridev.callsecurepro.ui.calls;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.ui.common.BaseMainSectionFragment;

public class CallsFragment extends BaseMainSectionFragment {

    @Override
    protected int getTitleRes() {
        return R.string.calls_title;
    }

    @Override
    protected int getSubtitleRes() {
        return R.string.calls_subtitle;
    }

    @Override
    protected int getIconTextRes() {
        return R.string.calls_icon_text;
    }

    @Override
    protected int getFeatureTitleRes() {
        return R.string.calls_feature_title;
    }

    @Override
    protected int getFeatureBodyRes() {
        return R.string.calls_feature_body;
    }

    @Override
    protected int getAccentColorRes() {
        return R.color.csp_safe;
    }

    @Override
    protected int getAccentContainerColorRes() {
        return R.color.csp_safe_container;
    }
}
