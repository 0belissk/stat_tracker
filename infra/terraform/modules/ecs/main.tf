locals {
  app_container_name      = "players-api"
  service_name            = "${var.name_prefix}-players-api"
  log_group_name          = "/ecs/${local.service_name}"
  base_app_environment    = [
    {
      name  = "SPRING_PROFILES_ACTIVE"
      value = "prod"
    },
    {
      name  = "AWS_XRAY_DAEMON_ADDRESS"
      value = "127.0.0.1:2000"
    },
    {
      name  = "XRAY_SERVICE_NAME"
      value = "${var.name_prefix}-players-api"
    },
    {
      name  = "XRAY_SAMPLING_STRATEGY"
      value = "default"
    }
  ]
  extra_app_environment   = [
    for name in sort(keys(var.app_environment)) : {
      name  = name
      value = var.app_environment[name]
    }
  ]
  combined_app_environment = concat(local.base_app_environment, local.extra_app_environment)
}

resource "aws_ecs_cluster" "this" {
  name = "${var.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-cluster" })
}

resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb-sg"
  description = "Allow inbound HTTP/S traffic to the ALB"
  vpc_id      = var.vpc_id

  dynamic "ingress" {
    for_each = var.listener_certificate_arn == null ? toset([80]) : toset([80, 443])
    content {
      description = "Public access"
      from_port   = ingress.value
      to_port     = ingress.value
      protocol    = "tcp"
      cidr_blocks = var.allowed_ingress_cidrs
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-alb-sg" })
}

resource "aws_security_group" "service" {
  name        = "${var.name_prefix}-service-sg"
  description = "Allow traffic from ALB to ECS tasks"
  vpc_id      = var.vpc_id

  ingress {
    description     = "From ALB"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-service-sg" })
}

resource "aws_lb" "app" {
  name               = "${var.name_prefix}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids
  idle_timeout       = 60

  tags = merge(var.tags, { Name = "${var.name_prefix}-alb" })
}

resource "aws_lb_target_group" "app" {
  name        = "${var.name_prefix}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = var.health_check_path
    matcher             = "200"
    healthy_threshold   = 3
    unhealthy_threshold = 2
    interval            = 30
    timeout             = 5
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-tg" })
}

resource "aws_lb_listener" "http" {
  count             = var.listener_certificate_arn == null ? 1 : 0
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_lb_listener" "http_redirect" {
  count             = var.listener_certificate_arn == null ? 0 : 1
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      status_code = "HTTP_301"
      port        = "443"
      protocol    = "HTTPS"
    }
  }
}

resource "aws_lb_listener" "https" {
  count             = var.listener_certificate_arn == null ? 0 : 1
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.listener_certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = local.log_group_name
  retention_in_days = var.log_retention_in_days

  tags = merge(var.tags, { Service = local.service_name })
}

resource "aws_ecs_task_definition" "this" {
  family                   = local.service_name
  cpu                      = tostring(var.task_cpu)
  memory                   = tostring(var.task_memory)
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  container_definitions = jsonencode([
    {
      name      = local.app_container_name
      image     = var.container_image
      essential = true
      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]
      environment = local.combined_app_environment
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.region
          awslogs-stream-prefix = local.app_container_name
        }
      }
    },
    {
      name      = "xray-daemon"
      image     = var.xray_daemon_image
      essential = false
      portMappings = [
        {
          containerPort = 2000
          protocol      = "udp"
        }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "xray"
        }
      }
    }
  ])

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  tags = merge(var.tags, { Service = local.service_name })
}

resource "aws_ecs_service" "this" {
  name            = local.service_name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = local.app_container_name
    container_port   = var.container_port
  }

  health_check_grace_period_seconds = 60

  tags = merge(var.tags, { Service = local.service_name })
}

resource "aws_cloudwatch_dashboard" "app" {
  dashboard_name = "${local.service_name}-observability"
  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          view    = "timeSeries"
          region  = var.region
          title   = "ALB Target Response Time"
          stat    = "p95"
          period  = 60
          metrics = [
            [
              "AWS/ApplicationELB",
              "TargetResponseTime",
              "TargetGroup",
              aws_lb_target_group.app.arn_suffix,
              "LoadBalancer",
              aws_lb.app.arn_suffix
            ]
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          view    = "timeSeries"
          region  = var.region
          title   = "ALB Target 5XX Count"
          stat    = "Sum"
          period  = 60
          metrics = [
            [
              "AWS/ApplicationELB",
              "HTTPCode_Target_5XX_Count",
              "TargetGroup",
              aws_lb_target_group.app.arn_suffix,
              "LoadBalancer",
              aws_lb.app.arn_suffix
            ]
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          view    = "timeSeries"
          region  = var.region
          title   = "ALB 5XX Count"
          stat    = "Sum"
          period  = 60
          metrics = [
            [
              "AWS/ApplicationELB",
              "HTTPCode_ELB_5XX_Count",
              "LoadBalancer",
              aws_lb.app.arn_suffix
            ]
          ]
        }
      }
    ]
  })

  depends_on = [aws_ecs_service.this]
}

resource "aws_cloudwatch_metric_alarm" "target_5xx" {
  alarm_name          = "${local.service_name}-target-5xx"
  alarm_description   = "players-api is returning 5xx responses behind the ALB"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = var.target_5xx_alarm_evaluation_periods
  datapoints_to_alarm = var.target_5xx_alarm_datapoints
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = var.target_5xx_alarm_threshold
  treat_missing_data  = "notBreaching"

  dimensions = {
    TargetGroup = aws_lb_target_group.app.arn_suffix
    LoadBalancer = aws_lb.app.arn_suffix
  }

  alarm_actions = var.target_5xx_alarm_actions

  tags = merge(var.tags, { Service = local.service_name })
}
