resource "aws_lambda_function" "mattermost_figma_lambda_function" {
  runtime          = "${var.lambda_runtime}"
  function_name = "mattermost_figma_lambda_function"
  s3_bucket = "${var.figma_aws_lambda_bucket}"
  s3_key    = "${var.figma_aws_lambda_bucket_key}"
  handler          = "${var.lambda_function_handler}"
  timeout = 60
  memory_size = 256
  role             = "${aws_iam_role.iam_role_for_lambda.arn}"
  depends_on   = ["aws_s3_bucket_object.figma-jar","aws_cloudwatch_log_group.log_group"]

}

resource "aws_lambda_permission" "mattermost_figma_lambda_function" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = "${aws_lambda_function.mattermost_figma_lambda_function.function_name}"
  principal     = "apigateway.amazonaws.com"
  # The /*/* portion grants access from any method on any resource
  # within the API Gateway "REST API".
  source_arn = "${aws_api_gateway_deployment.java_lambda_deploy.execution_arn}/*/*"
}