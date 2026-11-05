class AuthUser {
  const AuthUser({
    required this.id,
    required this.fullName,
    required this.email,
    required this.roles,
  });

  final String id;
  final String fullName;
  final String email;
  final Set<String> roles;

  factory AuthUser.fromLoginJson(Map<String, dynamic> json) {
    return AuthUser(
      id: json['userId'] as String,
      fullName: json['fullName'] as String,
      email: json['email'] as String,
      roles: ((json['roles'] as List<dynamic>?) ?? const [])
          .map((role) => role.toString())
          .toSet(),
    );
  }

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    return AuthUser(
      id: json['id'] as String,
      fullName: json['fullName'] as String,
      email: json['email'] as String,
      roles: ((json['roles'] as List<dynamic>?) ?? const [])
          .map((role) => role.toString())
          .toSet(),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'fullName': fullName,
        'email': email,
        'roles': roles.toList(),
      };
}
