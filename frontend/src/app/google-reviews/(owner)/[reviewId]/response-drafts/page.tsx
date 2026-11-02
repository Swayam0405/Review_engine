import { ReviewDraftsPage } from "../../../../../features/review-drafts/components/ReviewDraftsPage";

interface ReviewDraftsRouteProps {
  params: Promise<{ reviewId: string }>;
}

export default async function ReviewDraftsRoute({
  params,
}: ReviewDraftsRouteProps) {
  const { reviewId } = await params;
  return <ReviewDraftsPage reviewId={reviewId} />;
}
