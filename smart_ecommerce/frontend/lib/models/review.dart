class Review {
  const Review({
    required this.id,
    required this.userId,
    required this.userName,
    required this.productId,
    required this.productName,
    required this.rating,
    this.comment,
    this.createdAt,
    this.sellerReply,
    this.sellerReplyAt,
  });

  final String id;
  final String userId;
  final String userName;
  final String productId;
  final String productName;
  final int rating;
  final String? comment;
  final DateTime? createdAt;
  final String? sellerReply;
  final DateTime? sellerReplyAt;

  factory Review.fromJson(Map<String, dynamic> json) => Review(
        id: json['id'].toString(),
        userId: json['userId'].toString(),
        userName: json['userName']?.toString() ?? '',
        productId: json['productId'].toString(),
        productName: json['productName']?.toString() ?? '',
        rating: (json['rating'] as num?)?.toInt() ?? 0,
        comment: json['comment']?.toString(),
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
        sellerReply: json['sellerReply']?.toString(),
        sellerReplyAt: DateTime.tryParse(json['sellerReplyAt']?.toString() ?? ''),
      );
}

class ReviewSummary {
  const ReviewSummary({required this.average, required this.count});
  final double average;
  final int count;

  factory ReviewSummary.fromReviews(List<Review> reviews) {
    if (reviews.isEmpty) return const ReviewSummary(average: 0, count: 0);
    final total = reviews.fold<int>(0, (sum, review) => sum + review.rating);
    return ReviewSummary(average: total / reviews.length, count: reviews.length);
  }
}


class ReviewEligibility {
  const ReviewEligibility({
    required this.purchasedAndDelivered,
    required this.alreadyReviewed,
    required this.canReview,
  });

  final bool purchasedAndDelivered;
  final bool alreadyReviewed;
  final bool canReview;

  factory ReviewEligibility.fromJson(Map<String, dynamic> json) => ReviewEligibility(
    purchasedAndDelivered: json['purchasedAndDelivered'] == true,
    alreadyReviewed: json['alreadyReviewed'] == true,
    canReview: json['canReview'] == true,
  );
}
