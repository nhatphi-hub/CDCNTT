import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Shared Material 3 design system. Existing routes, widgets and handlers stay intact.
class AppTheme {
  AppTheme._();

  static OutlineInputBorder _inputBorder(Color color, {double width = 1}) =>
      OutlineInputBorder(
        borderRadius: BorderRadius.circular(15),
        borderSide: BorderSide(color: color, width: width),
      );

  static ThemeData get light {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.primary,
      brightness: Brightness.light,
      primary: AppColors.primary,
      onPrimary: AppColors.white,
      secondary: AppColors.accent,
      onSecondary: AppColors.white,
      surface: AppColors.white,
      onSurface: AppColors.black,
      error: AppColors.error,
      outline: AppColors.gray300,
      surfaceContainerLowest: AppColors.white,
      surfaceContainerLow: AppColors.gray100,
    );
    final subtleBorder = BorderSide(color: AppColors.gray300.withValues(alpha: .9));
    const transition = Duration(milliseconds: 180);
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.background,
      splashFactory: InkRipple.splashFactory,
      visualDensity: VisualDensity.standard,
      dividerColor: AppColors.gray300,
      // Native families remain available across Android, Windows and the Web,
      // with a Vietnamese-capable system fallback when needed.
      textTheme: const TextTheme(
        displaySmall: TextStyle(fontSize: 36, fontWeight: FontWeight.w800, height: 1.16, letterSpacing: -1.25, color: AppColors.black),
        headlineMedium: TextStyle(fontSize: 26, fontWeight: FontWeight.w800, height: 1.22, letterSpacing: -.75, color: AppColors.black),
        headlineSmall: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, height: 1.28, letterSpacing: -.35, color: AppColors.black),
        titleLarge: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, letterSpacing: -.3, color: AppColors.black),
        titleMedium: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: AppColors.black),
        titleSmall: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: AppColors.black),
        bodyLarge: TextStyle(fontSize: 16, height: 1.55, color: AppColors.black),
        bodyMedium: TextStyle(fontSize: 14, height: 1.5, color: AppColors.charcoal),
        bodySmall: TextStyle(fontSize: 12, height: 1.45, color: AppColors.gray700),
        labelLarge: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, letterSpacing: .1),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.white,
        foregroundColor: AppColors.black,
        centerTitle: false,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        titleTextStyle: TextStyle(color: AppColors.black, fontWeight: FontWeight.w800, fontSize: 19),
      ),
      dividerTheme: const DividerThemeData(color: AppColors.gray300, thickness: 1, space: 1),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.gray100,
        isDense: false,
        alignLabelWithHint: true,
        floatingLabelBehavior: FloatingLabelBehavior.auto,
        labelStyle: const TextStyle(color: AppColors.gray700, fontWeight: FontWeight.w600, fontSize: 14),
        floatingLabelStyle: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w700, fontSize: 14),
        hintStyle: const TextStyle(color: AppColors.gray500, fontSize: 14),
        helperStyle: const TextStyle(fontSize: 12, color: AppColors.gray700, height: 1.45),
        errorStyle: const TextStyle(color: AppColors.error, fontSize: 12, height: 1.5, fontWeight: FontWeight.w500),
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
        prefixIconColor: AppColors.gray700,
        suffixIconColor: AppColors.gray700,
        iconColor: AppColors.gray700,
        border: _inputBorder(AppColors.gray300),
        enabledBorder: _inputBorder(AppColors.gray300),
        disabledBorder: _inputBorder(AppColors.gray300.withValues(alpha: .55)),
        focusedBorder: _inputBorder(AppColors.primary, width: 2),
        errorBorder: _inputBorder(AppColors.error, width: 1.3),
        focusedErrorBorder: _inputBorder(AppColors.error, width: 2),
      ),
      textSelectionTheme: const TextSelectionThemeData(
        cursorColor: AppColors.primary,
        selectionColor: Color(0xFFCFDFFF),
        selectionHandleColor: AppColors.primary,
      ),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: AppColors.white,
        disabledBackgroundColor: AppColors.gray300,
        disabledForegroundColor: AppColors.gray700,
        elevation: 0,
        minimumSize: const Size(0, 52),
        padding: const EdgeInsets.symmetric(horizontal: 23, vertical: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14),
        animationDuration: transition,
      )),
      elevatedButtonTheme: ElevatedButtonThemeData(style: ElevatedButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: AppColors.white,
        elevation: 0,
        minimumSize: const Size(0, 50),
        padding: const EdgeInsets.symmetric(horizontal: 23, vertical: 13),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontWeight: FontWeight.w800),
        animationDuration: transition,
      )),
      outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(
        foregroundColor: AppColors.primaryDark,
        minimumSize: const Size(0, 50),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        side: const BorderSide(color: AppColors.gray300, width: 1.2),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
        animationDuration: transition,
      )),
      textButtonTheme: TextButtonThemeData(style: TextButton.styleFrom(
        foregroundColor: AppColors.primary,
        padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 11),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
        animationDuration: transition,
      )),
      iconButtonTheme: IconButtonThemeData(style: IconButton.styleFrom(
        foregroundColor: AppColors.charcoal,
        highlightColor: AppColors.primarySoft,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      )),
      cardTheme: CardThemeData(
        color: AppColors.white,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
        clipBehavior: Clip.antiAlias,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(19), side: subtleBorder),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: AppColors.white,
        surfaceTintColor: Colors.transparent,
        elevation: 10,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        titleTextStyle: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800, letterSpacing: -.4, color: AppColors.black),
      ),
      bottomSheetTheme: const BottomSheetThemeData(backgroundColor: AppColors.white, surfaceTintColor: Colors.transparent),
      popupMenuTheme: PopupMenuThemeData(
        color: AppColors.white,
        surfaceTintColor: Colors.transparent,
        elevation: 8,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.white,
        selectedColor: AppColors.primarySoft,
        side: const BorderSide(color: AppColors.gray300),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(11)),
        labelStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      ),
      checkboxTheme: CheckboxThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(5)),
        side: const BorderSide(color: AppColors.gray500, width: 1.5),
        fillColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected) ? AppColors.primary : null),
        checkColor: const WidgetStatePropertyAll(Colors.white),
      ),
      radioTheme: const RadioThemeData(fillColor: WidgetStatePropertyAll(AppColors.primary)),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected) ? AppColors.primary : AppColors.gray700),
        trackColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected) ? AppColors.primarySoft : AppColors.gray300),
      ),
      listTileTheme: const ListTileThemeData(
        contentPadding: EdgeInsets.symmetric(horizontal: 15, vertical: 5),
        iconColor: AppColors.primary,
        textColor: AppColors.black,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.white,
        elevation: 4,
        indicatorColor: AppColors.primarySoft,
        labelTextStyle: WidgetStateProperty.resolveWith((states) => TextStyle(
          fontSize: 11.5,
          fontWeight: states.contains(WidgetState.selected) ? FontWeight.w800 : FontWeight.w600,
          color: states.contains(WidgetState.selected) ? AppColors.primaryDark : AppColors.gray700,
        )),
      ),
      tabBarTheme: const TabBarThemeData(
        labelStyle: TextStyle(fontSize: 13, fontWeight: FontWeight.w800),
        unselectedLabelStyle: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
      ),
      tooltipTheme: TooltipThemeData(
        decoration: BoxDecoration(color: AppColors.black, borderRadius: BorderRadius.circular(9)),
        textStyle: const TextStyle(fontSize: 12, color: AppColors.white),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: AppColors.black,
        contentTextStyle: const TextStyle(color: AppColors.white, fontWeight: FontWeight.w600),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        elevation: 8,
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        backgroundColor: AppColors.primary,
        foregroundColor: AppColors.white,
        elevation: 3,
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(color: AppColors.primary),
    );
  }
}
