import 'package:flutter/widgets.dart';
import 'package:google_sign_in_web/web_only.dart' as web;

/// Google's official button: on web, calling GoogleSignIn.authenticate() is unsupported.
class PpeeGoogleSignInButton extends StatelessWidget {
  const PpeeGoogleSignInButton({super.key, this.disabled = false});
  final bool disabled;

  @override
  Widget build(BuildContext context) => IgnorePointer(
        ignoring: disabled,
        child: Opacity(
          opacity: disabled ? 0.5 : 1,
          child: SizedBox(
            height: 44,
            child: web.renderButton(
              configuration: web.GSIButtonConfiguration(
                type: web.GSIButtonType.standard,
                text: web.GSIButtonText.continueWith,
                theme: web.GSIButtonTheme.outline,
                size: web.GSIButtonSize.large,
                shape: web.GSIButtonShape.rectangular,
                minimumWidth: 260,
                locale: 'vi',
              ),
            ),
          ),
        ),
      );
}
