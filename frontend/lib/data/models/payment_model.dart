class PaymentModel {
  final String id;
  final String userId;
  final String? rideId;
  final double amount;
  final String status;
  final String paymentMethod;
  final String transactionReference;
  final DateTime createdAt;

  PaymentModel({
    required this.id,
    required this.userId,
    this.rideId,
    required this.amount,
    required this.status,
    required this.paymentMethod,
    required this.transactionReference,
    required this.createdAt,
  });

  factory PaymentModel.fromJson(Map<String, dynamic> json) {
    return PaymentModel(
      id: json['id'] ?? '',
      userId: json['userId'] ?? '',
      rideId: json['rideId'],
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      status: json['status'] ?? 'PENDING',
      paymentMethod: json['paymentMethod'] ?? 'WALLET',
      transactionReference: json['transactionReference'] ?? '',
      createdAt: json['createdAt'] != null ? DateTime.parse(json['createdAt']) : DateTime.now(),
    );
  }
}
