package com.tridev.callsecurepro.ui.contacts;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.ui.common.BaseMainSectionFragment;

public class ContactsFragment extends BaseMainSectionFragment {

    @Override
    protected int getTitleRes() {
        return R.string.contacts_title;
    }

    @Override
    protected int getSubtitleRes() {
        return R.string.contacts_subtitle;
    }

    @Override
    protected int getIconTextRes() {
        return R.string.contacts_icon_text;
    }

    @Override
    protected int getFeatureTitleRes() {
        return R.string.contacts_feature_title;
    }

    @Override
    protected int getFeatureBodyRes() {
        return R.string.contacts_feature_body;
    }

    @Override
    protected int getAccentColorRes() {
        return R.color.csp_tertiary;
    }

    @Override
    protected int getAccentContainerColorRes() {
        return R.color.csp_tertiary_container;
    }
}
