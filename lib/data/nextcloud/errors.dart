import 'package:local_saber/i18n/strings.g.dart';

abstract class LoginFailure implements Exception {
  final message = 'Login failed';
}

class EncLoginFailure implements LoginFailure {
  @override
  final message = t.login.encLoginStep.wrongEncPassword;
}
