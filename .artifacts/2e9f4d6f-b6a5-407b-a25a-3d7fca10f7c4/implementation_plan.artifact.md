# Implementation Plan - Performance Optimization

This plan addresses the significant delays reported when opening the app and when exporting reports.

## User Review Required

> [!IMPORTANT]
> I will be removing the **Tajawal** font from Google Fonts and switching to the **system default Arabic font**. This will make the app load significantly faster and work perfectly offline, but the font appearance may change slightly (it will use the standard Android Arabic font).

## Proposed Changes

### Core Logic & UI

#### [MODIFY] [index.html](file:///D:/مشاريع تطبيقات الاندرويد/LedgerApp1/app/src/main/assets/www/index.html)
- **Remove Network Dependencies**:
    - Remove Google Fonts preconnect and stylesheet links.
    - Update CSS to use `font-family: system-ui, -apple-system, sans-serif;`.
- **Optimize Calculations**:
    - Refactor `calcWorker(workerId)` to `calcWorkerData(worker, entry)` to avoid searching the `state.workers` array in a loop (O(N^2) to O(N)).
    - Update `cardsListHTML` and `renderTable` to use the optimized calculation.
- **Optimize Print/Export**:
    - Remove the Google Fonts link from the `doPrint` HTML template.
    - Minimize the HTML structure passed across the Javascript bridge.

### Android Bridge

#### [MODIFY] [MainActivity.java](file:///D:/مشاريع تطبيقات الاندرويد/LedgerApp1/app/src/main/java/com/ledger/daftar/MainActivity.java)
- **Optimize Print WebView**:
    - Set the background color and cache settings for the `printWebView` to match the main WebView.
    - Ensure `loadDataWithBaseURL` is used efficiently without waiting for non-existent external resources.

## Verification Plan

### Automated Tests
- None available for UI performance in this environment.

### Manual Verification
1. **App Launch**: Cold start the app. Verify that the transition from splash screen to the main list is fast.
2. **Offline Mode**: Turn off internet and verify that the app still loads and looks good.
3. **Export Speed**: Click "Export" on a worker's account. The print dialog should appear much faster than before.
4. **Data Integrity**: Verify that all calculations (Total Earnings, Remaining, etc.) are still accurate after the refactoring.
