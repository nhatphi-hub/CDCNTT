class Category {
  const Category({
    required this.id,
    required this.name,
    required this.slug,
    required this.isActive,
    this.parentId,
    this.description,
    this.image,
  });

  final String id;
  final String? parentId;
  final String name;
  final String slug;
  final String? description;
  final String? image;
  final bool isActive;

  factory Category.fromJson(Map<String, dynamic> json) => Category(
    id: json['id'].toString(),
    parentId: json['parentId']?.toString(),
    name: json['name']?.toString() ?? '',
    slug: json['slug']?.toString() ?? '',
    description: json['description']?.toString(),
    image: json['image']?.toString(),
    isActive: json['isActive'] == true,
  );
}
