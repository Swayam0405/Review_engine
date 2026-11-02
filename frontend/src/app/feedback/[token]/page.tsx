import { PublicFeedbackPage } from "../../../features/feedback/components/PublicFeedbackPage";

interface PublicFeedbackRouteProps {
  params: Promise<{ token: string }>;
}

export default async function PublicFeedbackRoute({
  params,
}: PublicFeedbackRouteProps) {
  const { token } = await params;
  return <PublicFeedbackPage token={token} />;
}
