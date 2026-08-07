package com.tridev.callsecurepro.ui.dial;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.FragmentDialBinding;
import com.tridev.callsecurepro.telecom.SimCallingManager;
import com.tridev.callsecurepro.ui.lookup.NumberLookupActivity;

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
    @Nullable
    private ContactMatch currentContactMatch;

    private boolean formattingNumber;
    private boolean simTelephonyAvailable;
    private boolean simPermissionGranted;
    private boolean simExactMappingSupported;
    @NonNull
    private String lastNumberPreferenceCheck = "";

    private final ActivityResultLauncher<String> phoneStatePermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (binding == null) {
                            return;
                        }
                        if (!granted) {
                            Toast.makeText(
                                    requireContext(),
                                    R.string.dial_sim_permission_denied,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                        refreshSimOptions();
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
        binding.backspaceButton.setOnClickListener(view -> deleteLastCharacter());
        binding.backspaceButton.setOnLongClickListener(view -> {
            clearDialNumber();
            return true;
        });

        binding.callButton.setOnClickListener(view -> placeCall());
        binding.moreOptionsButton.setOnClickListener(view -> showOverflowMenu());
    }

    private void showOverflowMenu() {
        if (binding == null) {
            return;
        }

        String number = getDialableNumber();
        PopupMenu popupMenu = new PopupMenu(requireContext(), binding.moreOptionsButton);
        popupMenu.inflate(R.menu.menu_dial_overflow);

        popupMenu.getMenu().findItem(R.id.menuDialClear).setEnabled(!number.isEmpty());
        popupMenu.getMenu().findItem(R.id.menuDialLookup).setEnabled(!number.isEmpty());
        popupMenu.getMenu().findItem(R.id.menuDialContact).setEnabled(!number.isEmpty());
        popupMenu.getMenu().findItem(R.id.menuDialContact).setTitle(
                currentContactMatch != null && currentContactMatch.contactUri != null
                        ? R.string.dial_menu_view_contact
                        : R.string.dial_menu_add_contact
        );

        boolean hasSpecificSimChoices = simTelephonyAvailable
                && simPermissionGranted
                && simExactMappingSupported
                && simOptions.size() > 1;
        popupMenu.getMenu().findItem(R.id.menuDialChooseSim)
                .setVisible(hasSpecificSimChoices);
        popupMenu.getMenu().findItem(R.id.menuDialEnableSim)
                .setVisible(simTelephonyAvailable && !simPermissionGranted);

        SimCallingManager.SimOption numberPreference = getNumberSpecificSimPreference(number);
        boolean canRemember = hasSpecificSimChoices
                && !number.isEmpty()
                && simCallingManager != null
                && !simCallingManager.isEmergencyNumber(number)
                && (numberPreference != null
                || (selectedSimOption != null && !selectedSimOption.isSystemDefault()));
        popupMenu.getMenu().findItem(R.id.menuDialRememberSim).setVisible(canRemember);
        popupMenu.getMenu().findItem(R.id.menuDialRememberSim).setTitle(
                numberPreference == null
                        ? R.string.dial_menu_remember_sim
                        : R.string.dial_menu_remove_saved_sim
        );

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menuDialPaste) {
                pasteNumberFromClipboard();
                return true;
            }
            if (itemId == R.id.menuDialClear) {
                clearDialNumber();
                return true;
            }
            if (itemId == R.id.menuDialLookup) {
                openCallerLookup();
                return true;
            }
            if (itemId == R.id.menuDialContact) {
                openOrAddContact();
                return true;
            }
            if (itemId == R.id.menuDialChooseSim) {
                showSimSelectionDialog();
                return true;
            }
            if (itemId == R.id.menuDialEnableSim) {
                requestSimAccess();
                return true;
            }
            if (itemId == R.id.menuDialRememberSim) {
                toggleNumberSimPreference();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void pasteNumberFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            showClipboardEmpty();
            return;
        }

        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            showClipboardEmpty();
            return;
        }

        CharSequence text = clipData.getItemAt(0).coerceToText(requireContext());
        if (text == null) {
            showClipboardEmpty();
            return;
        }

        String candidate = PhoneNumberUtils.extractNetworkPortion(text.toString().trim());
        if (candidate == null || candidate.trim().isEmpty()) {
            showClipboardEmpty();
            return;
        }
        setDialNumber(candidate.trim());
    }

    private void showClipboardEmpty() {
        Toast.makeText(
                requireContext(),
                R.string.dial_clipboard_empty,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void openCallerLookup() {
        String number = getDialableNumber();
        if (number.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_lookup_number_first,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Intent intent = new Intent(requireContext(), NumberLookupActivity.class);
        intent.putExtra(NumberLookupActivity.EXTRA_NUMBER, number);
        startActivity(intent);
    }

    private void openOrAddContact() {
        String number = getDialableNumber();
        if (number.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_contact_number_first,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Intent intent;
        if (currentContactMatch != null && currentContactMatch.contactUri != null) {
            intent = new Intent(Intent.ACTION_VIEW, currentContactMatch.contactUri);
        } else {
            intent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, number);
        }

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_contact_app_unavailable,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void requestSimAccess() {
        if (simCallingManager == null || simCallingManager.hasPhoneStatePermission()) {
            refreshSimOptions();
            return;
        }
        phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
    }

    private void showSimSelectionDialog() {
        if (simCallingManager == null) {
            return;
        }
        if (!simCallingManager.hasPhoneStatePermission()) {
            requestSimAccess();
            return;
        }
        if (!simExactMappingSupported || simOptions.size() <= 1) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_sim_no_accounts,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        CharSequence[] labels = new CharSequence[simOptions.size()];
        int selectedIndex = 0;
        for (int index = 0; index < simOptions.size(); index++) {
            SimCallingManager.SimOption option = simOptions.get(index);
            labels[index] = option.getLabel();
            if (selectedSimOption != null
                    && selectedSimOption.getStableKey().equals(option.getStableKey())) {
                selectedIndex = index;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dial_sim_selection_title)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    if (which >= 0 && which < simOptions.size()) {
                        selectSimOption(simOptions.get(which), true);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toggleNumberSimPreference() {
        if (simCallingManager == null) {
            return;
        }

        String number = getDialableNumber();
        if (number.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_sim_number_first,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (simCallingManager.isEmergencyNumber(number)) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_sim_emergency_system_route,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        SimCallingManager.SimOption existing = getNumberSpecificSimPreference(number);
        if (existing != null) {
            simCallingManager.clearSelectionForNumber(number);
            lastNumberPreferenceCheck = "";
            applyNumberSpecificSimPreference(number);
            Toast.makeText(
                    requireContext(),
                    R.string.dial_sim_removed_confirmation,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (selectedSimOption == null || selectedSimOption.isSystemDefault()) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_sim_choose_specific_first,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        simCallingManager.rememberSelectionForNumber(number, selectedSimOption);
        lastNumberPreferenceCheck = "";
        applyNumberSpecificSimPreference(number);
        Toast.makeText(
                requireContext(),
                R.string.dial_sim_saved_confirmation,
                Toast.LENGTH_SHORT
        ).show();
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

    private void clearDialNumber() {
        setDialNumber("");
        currentContactMatch = null;
    }

    private void setDialNumber(@NonNull String number) {
        if (binding == null) {
            return;
        }

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
        if (!contactsAllowed) {
            hideContactMatch();
        }
    }

    private void lookupContactMatch(@NonNull String number) {
        currentContactMatch = null;
        if (binding == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String normalized = PhoneNumberUtils.normalizeNumber(number);
        if (normalized == null
                || normalized.length() < 3
                || contactLookupExecutor == null
                || contactLookupExecutor.isShutdown()) {
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
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.LOOKUP_KEY,
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

            int idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID);
            int lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY);
            int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER);

            String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
            String matchedNumber = numberIndex >= 0 ? cursor.getString(numberIndex) : number;
            if (name == null || name.trim().isEmpty()) {
                return null;
            }

            Uri contactUri = null;
            if (idIndex >= 0 && lookupKeyIndex >= 0) {
                long contactId = cursor.getLong(idIndex);
                String lookupKey = cursor.getString(lookupKeyIndex);
                if (lookupKey != null && !lookupKey.trim().isEmpty()) {
                    contactUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                }
            }

            return new ContactMatch(
                    name.trim(),
                    matchedNumber == null ? number : matchedNumber.trim(),
                    contactUri
            );
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private void showContactMatch(@NonNull ContactMatch match) {
        currentContactMatch = match;
    }

    private void hideContactMatch() {
        currentContactMatch = null;
    }

    private void refreshSimOptions() {
        SimCallingManager manager = simCallingManager;
        ExecutorService executor = simExecutor;
        if (binding == null || manager == null || executor == null || executor.isShutdown()) {
            return;
        }

        int generation = simGeneration.incrementAndGet();
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
        simTelephonyAvailable = result.isTelephonyAvailable();
        simPermissionGranted = result.isPermissionGranted();
        simExactMappingSupported = result.isExactSimMappingSupported();

        simOptions.clear();
        simOptions.addAll(result.getOptions());
        selectedSimOption = null;
        lastNumberPreferenceCheck = "";

        if (simOptions.isEmpty()) {
            return;
        }

        SimCallingManager.SimOption initial = simCallingManager == null
                ? simOptions.get(0)
                : simCallingManager.resolveInitialSelection(getDialableNumber(), simOptions);
        selectSimOption(initial, false);
        applyNumberSpecificSimPreference(getDialableNumber());
    }

    private void selectSimOption(
            @NonNull SimCallingManager.SimOption option,
            boolean fromUser
    ) {
        selectedSimOption = option;
        if (fromUser && simCallingManager != null) {
            simCallingManager.rememberGlobalSelection(option);
        }
    }

    private void applyNumberSpecificSimPreference(@NonNull String number) {
        if (simCallingManager == null || simOptions.isEmpty()) {
            return;
        }

        String normalized = PhoneNumberUtils.normalizeNumber(number);
        if (normalized == null) {
            normalized = "";
        }
        if (normalized.equals(lastNumberPreferenceCheck)) {
            return;
        }
        lastNumberPreferenceCheck = normalized;

        SimCallingManager.SimOption resolved =
                simCallingManager.resolveInitialSelection(number, simOptions);
        if (resolved != null) {
            selectedSimOption = resolved;
        }
    }

    @Nullable
    private SimCallingManager.SimOption getNumberSpecificSimPreference(@NonNull String number) {
        if (simCallingManager == null || simOptions.isEmpty() || number.trim().isEmpty()) {
            return null;
        }
        return simCallingManager.resolveNumberSpecificSelection(number, simOptions);
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
            TelecomManager telecomManager = (TelecomManager) requireContext()
                    .getSystemService(Context.TELECOM_SERVICE);
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
        currentContactMatch = null;
        simCallingManager = null;
        binding = null;
        super.onDestroyView();
    }

    private static final class ContactMatch {
        @NonNull
        private final String name;
        @NonNull
        private final String number;
        @Nullable
        private final Uri contactUri;

        private ContactMatch(
                @NonNull String name,
                @NonNull String number,
                @Nullable Uri contactUri
        ) {
            this.name = name;
            this.number = number;
            this.contactUri = contactUri;
        }
    }
}
