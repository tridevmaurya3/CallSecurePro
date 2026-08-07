package com.tridev.callsecurepro.ui.dial;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.FragmentDialBinding;
import com.tridev.callsecurepro.telecom.SimCallingManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DialFragment extends Fragment {

    private static final String ARG_PHONE_NUMBER = "arg_phone_number";

    private FragmentDialBinding binding;
    private ExecutorService contactLookupExecutor;
    private ExecutorService simExecutor;
    private final AtomicInteger lookupGeneration = new AtomicInteger();
    private final AtomicInteger simGeneration = new AtomicInteger();
    private final List<SimCallingManager.SimOption> simOptions = new ArrayList<>();

    private SimCallingManager simCallingManager;
    @Nullable
    private SimCallingManager.SimOption selectedSimOption;

    private boolean formattingNumber;
    private boolean suppressRememberSwitch;
    @NonNull
    private String lastNumberPreferenceCheck = "";

    private final ActivityResultLauncher<String> phoneStatePermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (binding != null) {
                            refreshSimOptions();
                        }
                    }
            );

    @NonNull
    public static DialFragment newInstance(@Nullable String phoneNumber) {
        DialFragment fragment = new DialFragment();
        Bundle arguments = new Bundle();
        if (phoneNumber != null) {
            arguments.putString(ARG_PHONE_NUMBER, phoneNumber);
        }
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentDialBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        contactLookupExecutor = Executors.newSingleThreadExecutor();
        simExecutor = Executors.newSingleThreadExecutor();
        simCallingManager = new SimCallingManager(requireContext());

        setupNumberInput();
        setupKeypad();
        setupActions();
        setupSimSelection();
        refreshContactPermissionState();
        applyInitialNumberFromArguments();
        refreshSimOptions();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshContactPermissionState();
            lookupContactMatch(getDialableNumber());
            refreshSimOptions();
        }
    }

    private void setupNumberInput() {
        binding.dialNumberInput.setShowSoftInputOnFocus(false);
        binding.dialNumberInput.setSelection(binding.dialNumberInput.length());

        binding.dialNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action required.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Formatting is applied after the edit completes.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (formattingNumber || binding == null) {
                    return;
                }

                String current = editable == null ? "" : editable.toString();
                String formatted = formatPhoneNumber(current);

                if (!formatted.equals(current)) {
                    formattingNumber = true;
                    binding.dialNumberInput.setText(formatted);
                    binding.dialNumberInput.setSelection(formatted.length());
                    formattingNumber = false;
                }

                binding.dialNumberInputLayout.setError(null);
                String number = getDialableNumber();
                lookupContactMatch(number);
                applyNumberSpecificSimPreference(number);
            }
        });
    }

    private void setupKeypad() {
        binding.key1.setOnClickListener(view -> appendDialCharacter("1"));
        binding.key2.setOnClickListener(view -> appendDialCharacter("2"));
        binding.key3.setOnClickListener(view -> appendDialCharacter("3"));
        binding.key4.setOnClickListener(view -> appendDialCharacter("4"));
        binding.key5.setOnClickListener(view -> appendDialCharacter("5"));
        binding.key6.setOnClickListener(view -> appendDialCharacter("6"));
        binding.key7.setOnClickListener(view -> appendDialCharacter("7"));
        binding.key8.setOnClickListener(view -> appendDialCharacter("8"));
        binding.key9.setOnClickListener(view -> appendDialCharacter("9"));
        binding.keyStar.setOnClickListener(view -> appendDialCharacter("*"));
        binding.key0.setOnClickListener(view -> appendDialCharacter("0"));
        binding.keyHash.setOnClickListener(view -> appendDialCharacter("#"));

        binding.key0.setOnLongClickListener(view -> {
            String number = getDialableNumber();
            if (number.isEmpty()) {
                appendDialCharacter("+");
            }
            return true;
        });
    }

    private void setupActions() {
        binding.clearButton.setOnClickListener(view -> {
            binding.dialNumberInput.setText("");
            hideContactMatch();
            applyNumberSpecificSimPreference("");
        });

        binding.backspaceButton.setOnClickListener(view -> deleteLastCharacter());
        binding.backspaceButton.setOnLongClickListener(view -> {
            binding.dialNumberInput.setText("");
            hideContactMatch();
            applyNumberSpecificSimPreference("");
            return true;
        });

        binding.callButton.setOnClickListener(view -> placeCall());
    }

    private void setupSimSelection() {
        binding.simPermissionButton.setOnClickListener(view -> {
            if (simCallingManager == null || simCallingManager.hasPhoneStatePermission()) {
                refreshSimOptions();
                return;
            }
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
        });

        binding.rememberSimSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (suppressRememberSwitch || simCallingManager == null) {
                return;
            }

            String number = getDialableNumber();
            if (number.isEmpty()) {
                setRememberSwitchChecked(false);
                return;
            }

            if (checked && selectedSimOption != null) {
                simCallingManager.rememberSelectionForNumber(number, selectedSimOption);
            } else if (!checked) {
                simCallingManager.clearSelectionForNumber(number);
            }
            updateSimPreferenceSummary();
        });
    }

    private void applyInitialNumberFromArguments() {
        Bundle arguments = getArguments();
        if (arguments == null) {
            return;
        }

        String initialNumber = arguments.getString(ARG_PHONE_NUMBER);
        if (initialNumber == null || initialNumber.trim().isEmpty()) {
            return;
        }

        setDialNumber(initialNumber.trim());
    }

    private void appendDialCharacter(@NonNull String character) {
        String current = getDialableNumber();
        setDialNumber(current + character);
    }

    private void deleteLastCharacter() {
        String current = getDialableNumber();
        if (current.isEmpty()) {
            return;
        }
        setDialNumber(current.substring(0, current.length() - 1));
    }

    private void setDialNumber(@NonNull String number) {
        String formatted = formatPhoneNumber(number);
        formattingNumber = true;
        binding.dialNumberInput.setText(formatted);
        binding.dialNumberInput.setSelection(formatted.length());
        formattingNumber = false;
        binding.dialNumberInputLayout.setError(null);

        String dialable = getDialableNumber();
        lookupContactMatch(dialable);
        applyNumberSpecificSimPreference(dialable);
    }

    @NonNull
    private String getDialableNumber() {
        if (binding == null || binding.dialNumberInput.getText() == null) {
            return "";
        }

        return PhoneNumberUtils.stripSeparators(
                binding.dialNumberInput.getText().toString().trim()
        );
    }

    @NonNull
    private String formatPhoneNumber(@NonNull String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String dialable = PhoneNumberUtils.stripSeparators(trimmed);
        if (dialable.contains("*") || dialable.contains("#")) {
            return dialable;
        }

        String countryIso = Locale.getDefault().getCountry();
        String formatted = PhoneNumberUtils.formatNumber(dialable, countryIso);
        return formatted == null || formatted.trim().isEmpty() ? dialable : formatted;
    }

    private void refreshContactPermissionState() {
        boolean contactsAllowed = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED;

        binding.contactPermissionNote.setVisibility(contactsAllowed ? View.GONE : View.VISIBLE);
        if (!contactsAllowed) {
            hideContactMatch();
        }
    }

    private void lookupContactMatch(@NonNull String number) {
        if (binding == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
        ) != PackageManager.PERMISSION_GRANTED) {
            hideContactMatch();
            return;
        }

        String normalized = PhoneNumberUtils.normalizeNumber(number);
        if (normalized.length() < 3 || contactLookupExecutor == null
                || contactLookupExecutor.isShutdown()) {
            hideContactMatch();
            return;
        }

        int generation = lookupGeneration.incrementAndGet();
        contactLookupExecutor.execute(() -> {
            ContactMatch match = queryContactMatch(number);

            if (!isAdded() || generation != lookupGeneration.get()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (binding == null || generation != lookupGeneration.get()) {
                    return;
                }

                if (match == null) {
                    hideContactMatch();
                } else {
                    showContactMatch(match);
                }
            });
        });
    }

    @Nullable
    private ContactMatch queryContactMatch(@NonNull String number) {
        Uri lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
        );

        String[] projection = new String[]{
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER
        };

        try (Cursor cursor = requireContext().getContentResolver().query(
                lookupUri,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER);

            String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
            String matchedNumber = numberIndex >= 0 ? cursor.getString(numberIndex) : number;

            if (name == null || name.trim().isEmpty()) {
                return null;
            }

            return new ContactMatch(
                    name.trim(),
                    matchedNumber == null ? number : matchedNumber.trim()
            );
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private void showContactMatch(@NonNull ContactMatch match) {
        binding.contactMatchCard.setVisibility(View.VISIBLE);
        binding.contactMatchName.setText(match.name);
        binding.contactMatchNumber.setText(match.number);

        String trimmed = match.name.trim();
        String initial = trimmed.isEmpty()
                ? "#"
                : trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
        binding.contactMatchInitial.setText(initial);
    }

    private void hideContactMatch() {
        if (binding != null) {
            binding.contactMatchCard.setVisibility(View.GONE);
        }
    }

    private void refreshSimOptions() {
        FragmentDialBinding currentBinding = binding;
        SimCallingManager manager = simCallingManager;
        ExecutorService executor = simExecutor;
        if (currentBinding == null || manager == null || executor == null || executor.isShutdown()) {
            return;
        }

        int generation = simGeneration.incrementAndGet();
        currentBinding.simLoadingProgress.setVisibility(View.VISIBLE);
        currentBinding.simInfoText.setText(R.string.dial_sim_loading);

        executor.execute(() -> {
            SimCallingManager.LoadResult result = manager.loadOptions();
            if (!isAdded() || generation != simGeneration.get()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (binding == null || generation != simGeneration.get()) {
                    return;
                }
                renderSimOptions(result);
            });
        });
    }

    private void renderSimOptions(@NonNull SimCallingManager.LoadResult result) {
        binding.simLoadingProgress.setVisibility(View.GONE);
        binding.simChipGroup.removeAllViews();
        simOptions.clear();
        simOptions.addAll(result.getOptions());
        selectedSimOption = null;
        lastNumberPreferenceCheck = "";

        if (!result.isTelephonyAvailable()) {
            binding.simInfoText.setText(R.string.dial_sim_no_telephony);
            binding.simPermissionButton.setVisibility(View.GONE);
            binding.simChipGroup.setVisibility(View.GONE);
            binding.rememberSimSwitch.setVisibility(View.GONE);
            binding.simPreferenceText.setVisibility(View.GONE);
            binding.simStatusChip.setText(R.string.dial_sim_status_system);
            return;
        }

        if (!result.isPermissionGranted()) {
            binding.simInfoText.setText(R.string.dial_sim_permission_body);
            binding.simPermissionButton.setVisibility(View.VISIBLE);
            binding.simChipGroup.setVisibility(View.GONE);
            binding.rememberSimSwitch.setVisibility(View.GONE);
            binding.simPreferenceText.setVisibility(View.VISIBLE);
            binding.simPreferenceText.setText(R.string.dial_sim_system_preference);
            binding.simStatusChip.setText(R.string.dial_sim_status_system);
            if (!simOptions.isEmpty()) {
                selectedSimOption = simOptions.get(0);
            }
            return;
        }

        binding.simPermissionButton.setVisibility(View.GONE);

        int specificSimCount = Math.max(0, simOptions.size() - 1);
        if (!result.isExactSimMappingSupported()) {
            binding.simInfoText.setText(R.string.dial_sim_legacy_system_default);
        } else if (specificSimCount == 0) {
            binding.simInfoText.setText(R.string.dial_sim_no_accounts);
        } else if (specificSimCount == 1) {
            binding.simInfoText.setText(R.string.dial_sim_ready_one);
        } else {
            binding.simInfoText.setText(
                    getString(R.string.dial_sim_ready_many, specificSimCount)
            );
        }

        binding.simChipGroup.setVisibility(View.VISIBLE);
        for (SimCallingManager.SimOption option : simOptions) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(option.getLabel());
            chip.setTag(option.getStableKey());
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setCheckedIconVisible(true);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setOnClickListener(view -> selectSimOption(option, true));
            binding.simChipGroup.addView(chip);
        }

        SimCallingManager.SimOption initial = simCallingManager.resolveInitialSelection(
                getDialableNumber(),
                simOptions
        );
        selectSimOption(initial, false);

        boolean multipleSpecificSims = specificSimCount >= 2;
        binding.rememberSimSwitch.setVisibility(
                multipleSpecificSims ? View.VISIBLE : View.GONE
        );
        binding.simPreferenceText.setVisibility(View.VISIBLE);
        applyNumberSpecificSimPreference(getDialableNumber());
    }

    private void selectSimOption(
            @NonNull SimCallingManager.SimOption option,
            boolean fromUser
    ) {
        selectedSimOption = option;

        if (fromUser && simCallingManager != null) {
            simCallingManager.rememberGlobalSelection(option);
            if (binding.rememberSimSwitch.isChecked() && !getDialableNumber().isEmpty()) {
                simCallingManager.rememberSelectionForNumber(getDialableNumber(), option);
            }
        }

        updateSimChipAppearance();
        updateSimPreferenceSummary();
    }

    private void updateSimChipAppearance() {
        if (binding == null || selectedSimOption == null) {
            return;
        }

        int selectedBackground = ContextCompat.getColor(
                requireContext(),
                R.color.csp_primary_container
        );
        int selectedForeground = ContextCompat.getColor(
                requireContext(),
                R.color.csp_primary
        );
        int normalBackground = ContextCompat.getColor(
                requireContext(),
                R.color.csp_surface
        );
        int normalForeground = ContextCompat.getColor(
                requireContext(),
                R.color.csp_text_primary
        );

        for (int index = 0; index < binding.simChipGroup.getChildCount(); index++) {
            View child = binding.simChipGroup.getChildAt(index);
            if (!(child instanceof Chip)) {
                continue;
            }

            Chip chip = (Chip) child;
            boolean selected = selectedSimOption.getStableKey().equals(chip.getTag());
            chip.setChecked(selected);
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    selected ? selectedBackground : normalBackground
            ));
            chip.setTextColor(selected ? selectedForeground : normalForeground);
            chip.setCheckedIconTint(ColorStateList.valueOf(selectedForeground));
        }

        binding.simStatusChip.setText(selectedSimOption.getLabel());
    }

    private void applyNumberSpecificSimPreference(@NonNull String number) {
        if (simCallingManager == null || simOptions.isEmpty() || binding == null) {
            return;
        }

        String normalized = PhoneNumberUtils.normalizeNumber(number);
        if (normalized == null) {
            normalized = "";
        }
        if (normalized.equals(lastNumberPreferenceCheck)) {
            updateSimPreferenceSummary();
            return;
        }
        lastNumberPreferenceCheck = normalized;

        SimCallingManager.SimOption numberPreference =
                simCallingManager.resolveNumberSpecificSelection(number, simOptions);
        if (numberPreference != null) {
            setRememberSwitchChecked(true);
            selectSimOption(numberPreference, false);
        } else {
            setRememberSwitchChecked(false);
            updateSimPreferenceSummary();
        }
    }

    private void setRememberSwitchChecked(boolean checked) {
        if (binding == null) {
            return;
        }
        suppressRememberSwitch = true;
        binding.rememberSimSwitch.setChecked(checked);
        suppressRememberSwitch = false;
    }

    private void updateSimPreferenceSummary() {
        if (binding == null || selectedSimOption == null || simCallingManager == null) {
            return;
        }

        String number = getDialableNumber();
        if (!number.isEmpty() && simCallingManager.isEmergencyNumber(number)) {
            binding.simPreferenceText.setText(R.string.dial_sim_emergency_system_route);
            return;
        }

        SimCallingManager.SimOption numberPreference =
                simCallingManager.resolveNumberSpecificSelection(number, simOptions);
        if (numberPreference != null
                && numberPreference.getStableKey().equals(selectedSimOption.getStableKey())) {
            binding.simPreferenceText.setText(R.string.dial_sim_saved_for_number);
        } else if (selectedSimOption.isSystemDefault()) {
            binding.simPreferenceText.setText(R.string.dial_sim_system_preference);
        } else {
            binding.simPreferenceText.setText(R.string.dial_sim_global_preference);
        }
    }

    private void placeCall() {
        String number = getDialableNumber();
        if (number.isEmpty()) {
            binding.dialNumberInputLayout.setError(getString(R.string.dial_empty_number));
            binding.dialNumberInput.requestFocus();
            return;
        }

        Uri callUri = Uri.fromParts("tel", number, null);
        boolean directCallAllowed = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED;

        if (directCallAllowed) {
            if (simCallingManager != null
                    && binding.rememberSimSwitch.isChecked()
                    && selectedSimOption != null
                    && !simCallingManager.isEmergencyNumber(number)) {
                simCallingManager.rememberSelectionForNumber(number, selectedSimOption);
            }

            TelecomManager telecomManager = (TelecomManager) requireContext()
                    .getSystemService(android.content.Context.TELECOM_SERVICE);
            if (telecomManager != null) {
                try {
                    Bundle extras = simCallingManager == null
                            ? new Bundle()
                            : simCallingManager.createCallExtras(number, selectedSimOption);
                    telecomManager.placeCall(callUri, extras);
                    return;
                } catch (SecurityException | IllegalArgumentException ignored) {
                    // Fall through to the normal Android dialer below.
                }
            }
        }

        Intent dialIntent = new Intent(Intent.ACTION_DIAL, callUri);
        try {
            startActivity(dialIntent);
            if (!directCallAllowed) {
                Toast.makeText(
                        requireContext(),
                        R.string.dial_call_permission_fallback,
                        Toast.LENGTH_SHORT
                ).show();
            }
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_no_phone_app,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        lookupGeneration.incrementAndGet();
        simGeneration.incrementAndGet();

        if (contactLookupExecutor != null) {
            contactLookupExecutor.shutdownNow();
            contactLookupExecutor = null;
        }
        if (simExecutor != null) {
            simExecutor.shutdownNow();
            simExecutor = null;
        }

        simOptions.clear();
        selectedSimOption = null;
        simCallingManager = null;
        binding = null;
        super.onDestroyView();
    }

    private static final class ContactMatch {
        @NonNull
        private final String name;
        @NonNull
        private final String number;

        private ContactMatch(@NonNull String name, @NonNull String number) {
            this.name = name;
            this.number = number;
        }
    }
}
