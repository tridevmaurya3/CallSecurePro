package com.tridev.callsecurepro.ui.community;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tridev.callsecurepro.R;

/** Number Lookup action that forwards the current input into Community Network. */
public class CommunityReportButton extends MaterialButton {

    public CommunityReportButton(@NonNull Context context) {
        super(context);
        init();
    }

    public CommunityReportButton(
            @NonNull Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    public CommunityReportButton(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnClickListener(view -> openCommunityNetwork());
    }

    private void openCommunityNetwork() {
        View root = getRootView();
        TextInputEditText input = root.findViewById(R.id.numberInput);
        String number = input == null || input.getText() == null
                ? ""
                : input.getText().toString().trim();

        Intent intent = new Intent(getContext(), CommunityNetworkActivity.class);
        if (!number.isEmpty()) {
            intent.putExtra(CommunityNetworkActivity.EXTRA_NUMBER, number);
        }
        getContext().startActivity(intent);
    }
}
