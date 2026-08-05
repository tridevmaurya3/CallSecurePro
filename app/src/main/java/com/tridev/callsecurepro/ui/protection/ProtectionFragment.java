package com.tridev.callsecurepro.ui.protection;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.ui.common.BaseMainSectionFragment;

public class ProtectionFragment extends BaseMainSectionFragment {

    @Override
    protected int getTitleRes() {
        return R.string.protection_title;
    }

    @Override
    protected int getSubtitleRes() {
        return R.string.protection_subtitle;
    }

    @Override
    protected int getIconTextRes() {
        return R.string.protection_icon_text;
    }

    @Override
    protected int getFeatureTitleRes() {
        return R.string.protection_feature_title;
    }

    @Override
    protected int getFeatureBodyRes() {
        return R.string.protection_feature_body;
    }

    @Override
    protected int getAccentColorRes() {
        return R.color.csp_spam;
    }

    @Override
    protected int getAccentContainerColorRes() {
        return R.color.csp_spam_container;
    }
}
