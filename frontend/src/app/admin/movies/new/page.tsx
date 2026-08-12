"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { getGenres, createMovie } from "@/lib/api/admin-movies";
import type { GenreResponse } from "@/lib/api/types";
import { MovieForm } from "@/components/admin/movie-form";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function NewMoviePage() {
  const { callAuthorized } = useAuth();
  const router = useRouter();
  const [genres, setGenres] = useState<GenreResponse[] | null>(null);
  const [genresError, setGenresError] = useState(false);

  useEffect(() => {
    getGenres()
      .then(setGenres)
      .catch(() => setGenresError(true));
  }, []);

  return (
    <section className="mx-auto max-w-2xl px-6 py-10">
      <Link href="/admin/movies" className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden />
        返回电影列表
      </Link>

      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">新增电影</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          海报/背景图需要在电影创建之后单独上传——提交后会跳转到该电影的编辑页,在那里完成图片上传。
        </p>
      </header>

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
        </CardHeader>
        <CardContent>
          {genresError ? (
            <p className="py-8 text-center text-sm text-destructive">分类列表加载失败,请刷新重试</p>
          ) : !genres ? (
            <p className="py-8 text-center text-sm text-muted-foreground">加载中...</p>
          ) : (
            <MovieForm
              genres={genres}
              onSave={(request) => callAuthorized((token) => createMovie(token, request))}
              onSaved={(saved) => router.push(`/admin/movies/${saved.id}/edit?created=1`)}
              submitLabel="创建电影"
              submittingLabel="创建中…"
            />
          )}
        </CardContent>
      </Card>
    </section>
  );
}
